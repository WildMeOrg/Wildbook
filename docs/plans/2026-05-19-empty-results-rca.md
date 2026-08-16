OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e4160-8a4b-71e1-8a01-14b2f38fc718
--------
user
# Wildbook v2 ml-service migration — Codex review context bundle

You are reviewing code on the `migrate-ml-service-v2` branch of the
Wildbook repo (`/mnt/c/Wildbook-clean2`). This bundle gives you the
project conventions, repo gotchas, and current architecture that the
code under review assumes.

## Repo facts

- **Stack:** Java 17, Tomcat 9, DataNucleus 5.2.7 (JDO), PostgreSQL 13,
  OpenSearch 2.15 (3.1 on the live amphibian-reptile deployment),
  React 18.
- **Persistence:** JDO with manual transactions via the `Shepherd`
  class. Not Hibernate, not JPA.
- **Indexing:** OpenSearch is **async** from JDO writes. An
  `IndexingManager` background thread picks up dirty entities and
  pushes them to OS; OS additionally has its own refresh interval
  (~1s default).
- **Branch context:** v2 of the ml-service migration. v1 was abandoned
  on `migrate-ml-service`. Current branch (`migrate-ml-service-v2`)
  has the 20 v2 commits plus the Track 1 empty-match-prospects work
  in progress. See
  `docs/plans/2026-05-09-ml-service-migration-v2.md` and
  `docs/plans/2026-05-18-empty-match-prospects-design.md`.

## Shepherd pattern

```java
Shepherd shep = new Shepherd(context);
shep.setAction(ACTION_PREFIX + "methodName");
try {
    shep.beginDBTransaction();
    // ... JDO operations ...
    shep.commitDBTransaction();
} catch (Exception ex) {
    // log
} finally {
    shep.rollbackAndClose();
}
```

`rollbackAndClose` is idempotent — safe after commit and safe after
early return.

**Critical gotcha:** never hold a Shepherd open across a network call.
The v2 polling-thread design (commit `c6ffe5d20`) uses a Phase A / B /
C pattern: load a detached DTO under Shepherd in Phase A, do the HTTP
work without Shepherd in Phase B, persist outcome in a fresh Shepherd
in Phase C.

## JDO naming

`@PrimaryKey` field → PostgreSQL column `ID` (or domain-specific
`CATALOGNUMBER` for `ENCOUNTER`, `INDIVIDUALID` for `MARKEDINDIVIDUAL`).
Join tables use `_OID` (owner) and `_EID` (element) suffixes. The
`EMBEDDING` table uses `ANNOTATION_ID` (no `_OID` suffix — it's a
direct FK, not a JDO-generated join).

## OpenSearch async indexing — visibility gotcha

`OpenSearch.indexRefresh(indexName)` forces a Lucene refresh
boundary; **does not** drain the Wildbook IndexingManager queue. If
you need "after this write the doc must be searchable" semantics,
use `OpenSearch.waitForVisibility(indexName, ids, timeoutMs)` (added
in c7, commit `f429c5bf8`).

## IA.json structure (ml-service v2)

```jsonc
{
  "default": {
    "_id_conf": {
      "default": {
        "pipeline_root": "vector",   // "vector" = ml-service v2
        "method": "miewid-msv4.1",   // embedding model id
        "version": "4.1",
        "embedding_dimension": 2152,
        // legacy entries have api_endpoint instead of method/version
      }
    },
    "_mlservice_conf": {
      "default": {
        "base_url": "https://ml-service.example.com:8008",
        "detection_endpoint": "/pipeline/",
        "extraction_endpoint": "/extract/",
        "model_id": "...",
        "match_against_species": [...]
      }
    }
  }
}
```

`Embedding.findMatchProspects` gates entry on
`isVectorConfig = method != null || api_endpoint != null` — both
nullable independently.

## v1 antipatterns to avoid

1. **Don't hold Shepherd across HTTP.** Phase A/B/C pattern instead.
2. **Don't accept null returns ambiguously.** `null` means "we
   couldn't tell" — distinct enums for "no work" vs "failed" vs
   "rejected".
3. **Don't park silently.** Every parked annotation logs why with
   the original error string available for ops.
4. **Don't write large commits.** v1 wrote 800 lines and asked for
   review; v2 keeps commits to ~80 lines avg with design + code
   review per commit.

## CRLF/LF gotcha on this Windows-mounted repo

The Edit tool sometimes flips LF files to CRLF when editing on
`/mnt/c/Wildbook-clean2`. Reviewers should call this out if they
see `git ls-files --eol` reporting `i/lf w/crlf`.

## What we want from this review

Code-review the diff below. Focus on:
- Correctness given the Wildbook conventions above.
- Whether the implementation matches the locked design.
- Test coverage and gaps.
- Anything else.

**Do not write to any file.** Review-only.

---

# Empty match-results UI — independent root cause analysis

I've been debugging this iteratively across 14 commits (Track 1+2
empty-match-prospects, C12 bbox, C13 PairX-as-Phase-A/B/C, C14
SPA-fallback) and the React UI **still renders empty** despite the
backend looking 100% correct.

Step back and find the real root cause. I may have been treating
symptoms or fixing the wrong layer. Don't just code-review; do a
holistic investigation across backend Java, frontend React, and the
API contract between them.

## Confirmed-working backend state (this is real, verified by SQL + curl)

For the most recent test import `3eac65f3-04ad-4e67-b729-a326847b08bc`,
the database has 4 MatchResults attached to subTasks (matchTask
counterparts have no MR, as in v2 design):

| matchTask | subTask | MR | prospects |
|---|---|---|---|
| 5c61b82c | ed6c64a8 | f6439546 | 2 |
| 2809248f | bfda05bc | d55a1df9 | 2 |
| 25954d8c | caf6398b | 1b2bb236 | 4 |
| c2fef36e | ad13db33 | f6439546 | 4 |

(One ID typo in the table above is intentional — both `c2fef36e` and
`5c61b82c` map to subTasks with `f6439546` per my earlier query
output; the actual data has 4 distinct MRs.)

12 prospects total. Every prospect's `MATCHRESULTPROSPECT.ASSET_ID_OID`
is populated. The inspection PNG files exist on disk in the
AssetStore (verified by `ls -la`) and serve as `HTTP 200` via
`curl 'http://localhost:8080/wildbook_data_dir/<path>'`.

## Frontend state

- React page route: `/react/match-results?taskId=<id>`
- After C14, that URL now returns `HTTP 200` (was 404 with React HTML
  body before, which broke the PWA service worker's NetworkFirst
  cache).
- Browser loads the React shell, then the page renders **empty** —
  no prospect cards, no error message visible.
- Tested with the subTask ID `ed6c64a8` (which DOES have an MR with
  2 prospects + inspection MediaAssets) — still empty.

## What I want from you

Investigate, independently:

1. **Find the React component(s) and API endpoint(s) involved in
   the `/react/match-results` page.** Browse the frontend code in
   `/mnt/c/Wildbook-clean2/frontend/src/`. Identify:
   - Which component handles this route
   - What API call(s) it makes (URL pattern, expected response shape)
   - Where it would render an empty state vs prospects-list

2. **Find the corresponding backend servlet/API endpoint.** Likely
   under `/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/servlet/`
   or `.../api/`. Verify:
   - That the endpoint exists at the URL the React app calls
   - How it queries MatchResults — does it take a taskId and look up
     MR-by-task directly, or does it walk task children?
   - What shape the JSON response has, and whether it would serialize
     a MatchResultProspect with an inspection MediaAsset correctly

3. **Compare the frontend's expectations against the backend's
   response.** This is where I suspect the real bug is. The
   MatchResultProspect.jsonForApiGet at
   `src/main/java/org/ecocean/ia/MatchResultProspect.java:63-75`
   produces `{annotation, score, asset}` per prospect. Does the React
   component look for those exact keys?

4. **Anything else** — e.g., is there an authentication / ACL filter
   that's rejecting the request? A `project` scope that's filtering
   out the prospects? A timing issue with the React app's initial
   fetch?

## Past work (so you can rule out paths I already covered)

Track 1+2 (C1-C11): matching now finds prospects (4895 candidates
narrows to 1-3 strong matches per query annotation; that's good).
C12: fixed PairX bbox-clamp and shared-array bugs. C13: moved PairX
out of MatchResult constructor into a Phase-A/B/C enricher so
Shepherd isn't held across HTTP. C14: SPA fallback returns HTTP 200
instead of 404.

Backend correctness as far as the database/disk: verified.

The user explicitly asked me to seek your independent perspective
because I may be missing something. Don't just confirm what I've
done — look fresh.

**Do not write to any file.** Investigation + analysis only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll keep this review-only and trace the route from React entrypoint to API call to Java servlet/serializer. I’m going to start by locating the `/react/match-results` route and any matching backend endpoints, then compare the exact JSON contract.
exec
/bin/bash -lc 'rg -n "match-results|MatchResults|match results|matchResults|match_result|MatchResult" src/main/java/org/ecocean' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "react/match-results|match-results" .' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "match-results|MatchResults|match results|matchResults|match_result|MatchResult" frontend/src' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short && git branch --show-current' in /mnt/c/Wildbook-clean2
 succeeded in 1824ms:
frontend/src/AuthenticatedSwitch.jsx:31:const MatchResults = lazy(
frontend/src/AuthenticatedSwitch.jsx:32:  () => import("./pages/MatchResultsPage/MatchResults"),
frontend/src/AuthenticatedSwitch.jsx:86:            <Route path="/match-results" element={<MatchResults />} />
frontend/src/locale/en.json:705:    "CLICK_ANNOTATION_TO_SEE_MATCH_RESULTS": "Click on an annotation to view match results",
frontend/src/locale/en.json:829:    "NO_MATCH_RESULTS_AVAILABLE": "No match results available yet. The job may still be processing, or there may be no data. Please refresh the page to check again.",
frontend/src/constants/queryKeys.js:34:export function getSightingMatchResultsQueryKey(guid) {
frontend/src/constants/queryKeys.js:35:  return ["sightingMatchResults", guid];
frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx:4:import NewIndividualCreatedModal from "../../../pages/MatchResultsPage/components/NewIndividualCreatedModal";
frontend/src/pages/BulkImport/BulkImportTask.jsx:216:          const link = `/react/match-results?taskId=${arr[0]}`;
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:1:import MatchResultsStore from "../../../pages/MatchResultsPage/stores/matchResultsStore";
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:17:  matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:39:  matchResultsRoot: makeProspect(nodeOverrides),
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:44:describe("MatchResultsStore — initial state", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:47:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:89:describe("MatchResultsStore — setters", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:92:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:155:describe("MatchResultsStore — loadData", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:158:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:163:      matchResultsRoot: makeProspect({
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:165:        matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:181:  test("clears state when matchResultsRoot is null", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:182:    store.loadData({ matchResultsRoot: null });
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:232:describe("MatchResultsStore — _processData", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:235:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:387:describe("MatchResultsStore — currentViewData computed", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:390:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:407:describe("MatchResultsStore — setSelectedMatch", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:410:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:455:describe("MatchResultsStore — clearSelection / resetSelectionToQuery", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:458:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:478:describe("MatchResultsStore — computed: querySelectionItem", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:481:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:500:describe("MatchResultsStore — computed: selectedIncludingQuery", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:503:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:531:describe("MatchResultsStore — computed: uniqueIndividualIds", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:534:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:559:describe("MatchResultsStore — computed: matchingState", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:562:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:573:        matchResults: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:574:          ...makeProspect().matchResults,
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:576:            ...makeProspect().matchResults.queryAnnotation,
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:618:describe("MatchResultsStore — handleNoFurtherActionNeeded", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:620:    const store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:630:describe("MatchResultsStore — fetchMatchResults", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:633:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:638:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:646:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:648:      expect.stringContaining("/api/v3/tasks/task-abc/match-results"),
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:659:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:668:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:675:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:682:    await store.fetchMatchResults();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:689:describe("MatchResultsStore — handleCreateNewIndividual", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:692:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:762:describe("MatchResultsStore — handleMatch", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:765:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:807:describe("MatchResultsStore — handleMerge", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:810:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:4:import MatchResultsBottomBar from "../../../pages/MatchResultsPage/components/MatchResultsBottomBar";
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:16:  "../../../pages/MatchResultsPage/components/CreateNewIndividualModal",
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:42:  "../../../pages/MatchResultsPage/components/NewIndividualCreatedModal",
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:59:  "../../../pages/MatchResultsPage/components/MatchConfirmedModal",
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:106:      <MatchResultsBottomBar
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:116:describe("MatchResultsBottomBar — no_individuals state", () => {
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:161:describe("MatchResultsBottomBar — single_individual state", () => {
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:202:describe("MatchResultsBottomBar — two_individuals state", () => {
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:235:describe("MatchResultsBottomBar — too_many_individuals state", () => {
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:248:describe("MatchResultsBottomBar — no_further_action_needed state", () => {
frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx:268:describe("MatchResultsBottomBar — Cancel button", () => {
frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js:79:    expect(latestMatchItem).toHaveTextContent("/react/match-results?taskId=123");
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:13:import MatchResults from "../../../pages/MatchResultsPage/MatchResults";
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:37:  "../../../pages/MatchResultsPage/components/MatchProspectTable",
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:73:  "../../../pages/MatchResultsPage/components/MatchResultsBottomBar",
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:76:    function MatchResultsBottomBar() {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:79:    MatchResultsBottomBar.displayName = "MatchResultsBottomBar";
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:80:    return MatchResultsBottomBar;
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:85:  "../../../pages/MatchResultsPage/components/InstructionsModal",
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:106:  "../../../pages/MatchResultsPage/components/MatchCriteriaDrawer",
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:160:  matchResultsRoot: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:167:    matchResults: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:188:const renderComponent = (url = "/match-results?taskId=task-1") =>
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:192:        <MatchResults />
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:199:describe("MatchResults component", () => {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:219:  test("shows 'no match results' message when no taskId in URL", async () => {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:220:    renderComponent("/match-results");
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:369:  test("shows 'no match results' message when API returns empty prospects", async () => {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:372:        matchResultsRoot: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:375:          matchResults: {
frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx:4:import MatchCriteriaDrawer from "../../../pages/MatchResultsPage/components/MatchCriteriaDrawer";
frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx:4:import MatchConfirmedModal from "../../../pages/MatchResultsPage/components/MatchConfirmedModal";
frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx:4:import InstructionsModal from "../../../pages/MatchResultsPage/components/InstructionsModal";
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:4:} from "../../../pages/MatchResultsPage/helperFunctions";
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:14:    matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:46:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:47:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:60:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:61:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:117:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:118:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:131:    test("returns common object when no matchResults but task is running", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:132:      const node = makeNode({ matchResults: null });
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:142:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:143:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:172:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:173:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:199:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:200:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:210:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:211:          ...makeNode().matchResults,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:228:        matchResults: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:229:          queryAnnotation: makeNode().matchResults.queryAnnotation,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:238:        matchResults: {
frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx:4:import CreateNewIndividualModal from "../../../pages/MatchResultsPage/components/CreateNewIndividualModal";
frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js:215:        "/react/match-results?taskId=t123",
frontend/src/pages/Encounter/MatchCriteria.jsx:133:                  const url = `/react/match-results?taskId=${result?.data?.taskId}`;
frontend/src/__tests__/pages/Encounter/ImageModal.test.js:217:  test("match results button opens iaResults when selected annotation exists", () => {
frontend/src/__tests__/pages/Encounter/ImageModal.test.js:228:      "/react/match-results?taskId=task-123",
frontend/src/__tests__/pages/Encounter/ImageModal.test.js:233:  test("match results button is disabled when matchResultClickable is false", () => {
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:46:jest.mock("../../../components/icons/MatchResultIcon", () => () => (
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:305:    expect(url).toContain("/react/match-results?taskId=TASK-99");
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:374:    expect(url).toContain("/react/match-results?taskId=TASK-FR-1");
frontend/src/__tests__/pages/Encounter/ImageCard.test.js:395:      "Select an annotation to view match results.",
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:17:export default class MatchResultsStore {
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:86:    const root = results?.matchResultsRoot;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:96:    const rootHasMatchResults = !!root?.matchResults;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:100:      !rootHasMatchResults &&
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:107:      !rootHasMatchResults &&
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:402:  async fetchMatchResults({ silent = false } = {}) {
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:425:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:437:        toast.error("Failed to load match results");
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:447:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:457:        const root = result?.data?.matchResultsRoot;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:471:          "Failed to silently refresh match results: " +
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:513:      this.fetchMatchResults();
frontend/src/pages/Encounter/ImageCard.jsx:9:import MatchResultIcon from "../../components/icons/MatchResultIcon";
frontend/src/pages/Encounter/ImageCard.jsx:707:                  const url = `/react/match-results?taskId=${encodeURIComponent(taskId)}`;
frontend/src/pages/Encounter/ImageCard.jsx:748:                    const url = `/react/match-results?taskId=${encodeURIComponent(selectedAnnotation.iaTaskId)}`;
frontend/src/pages/Encounter/ImageCard.jsx:751:                    alert("No match results available for this annotation.");
frontend/src/pages/Encounter/ImageCard.jsx:754:                  alert("No match results available for this annotation.");
frontend/src/pages/Encounter/ImageCard.jsx:756:                  alert("Select an annotation to view match results.");
frontend/src/pages/Encounter/ImageCard.jsx:760:              <MatchResultIcon />
frontend/src/pages/MatchResultsPage/MatchResults.jsx:6:import MatchResultsStore from "./stores/matchResultsStore";
frontend/src/pages/MatchResultsPage/MatchResults.jsx:8:import MatchResultsBottomBar from "./components/MatchResultsBottomBar";
frontend/src/pages/MatchResultsPage/MatchResults.jsx:20:const MatchResults = observer(() => {
frontend/src/pages/MatchResultsPage/MatchResults.jsx:22:  const store = useMemo(() => new MatchResultsStore(), []);
frontend/src/pages/MatchResultsPage/MatchResults.jsx:66:      store.fetchMatchResults();
frontend/src/pages/MatchResultsPage/MatchResults.jsx:82:      await store.fetchMatchResults({ silent: true });
frontend/src/pages/MatchResultsPage/MatchResults.jsx:97:    return <FullScreenLoader data-testid="match-results-loader" />;
frontend/src/pages/MatchResultsPage/MatchResults.jsx:105:      id="match-results-page"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:106:      data-testid="match-results-page"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:122:        <div data-testid="match-results-bottom-bar">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:123:          <MatchResultsBottomBar
frontend/src/pages/MatchResultsPage/MatchResults.jsx:134:          data-testid="match-results-bottom-bar-spacer"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:140:        id="match-results-header"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:141:        data-testid="match-results-header"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:144:          <h2 id="match-results-title" data-testid="match-results-title">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:153:            id="match-results-instructions-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:154:            data-testid="match-results-instructions-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:158:              data-testid="match-results-instructions-icon"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:166:        id="match-results-toolbar"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:167:        data-testid="match-results-toolbar"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:171:          id="match-results-viewmode"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:172:          data-testid="match-results-viewmode"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:177:            id="match-results-viewmode-individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:178:            data-testid="match-results-viewmode-individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:202:            id="match-results-viewmode-image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:203:            data-testid="match-results-viewmode-image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:228:          id="match-results-controls"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:229:          data-testid="match-results-controls"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:233:            id="match-results-num-results-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:234:            data-testid="match-results-num-results-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:241:                data-testid="match-results-num-results-hint"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:252:              id="match-results-num-results-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:253:              data-testid="match-results-num-results-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:256:                id="match-results-num-results-input"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:257:                data-testid="match-results-num-results-input"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:271:                    store.fetchMatchResults();
frontend/src/pages/MatchResultsPage/MatchResults.jsx:282:                  id="match-results-num-results-apply"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:283:                  data-testid="match-results-num-results-apply"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:285:                  onClick={() => store.fetchMatchResults()}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:311:            id="match-results-project-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:312:            data-testid="match-results-project-group"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:320:              id="match-results-project-select-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:321:              data-testid="match-results-project-select-wrapper"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:355:            id="match-results-filter-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:356:            data-testid="match-results-filter-trigger"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:365:            <FilterIcon data-testid="match-results-filter-icon" />
frontend/src/pages/MatchResultsPage/MatchResults.jsx:370:      <div id="match-results-content" data-testid="match-results-content">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:374:            id="match-results-empty"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:375:            data-testid="match-results-empty"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:379:              defaultMessage="No match results available."
frontend/src/pages/MatchResultsPage/MatchResults.jsx:383:          <div id="match-results-sections" data-testid="match-results-sections">
frontend/src/pages/MatchResultsPage/MatchResults.jsx:388:                  id={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:389:                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:437:export default MatchResults;
frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx:162:          src={`${process.env.PUBLIC_URL}/images/MatchResultExample.png`}
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:35:const MatchResultsBottomBar = observer(
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:441:          id="match-results-bottom-bar"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:442:          data-testid="match-results-bottom-bar"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:447:            id="match-results-bottom-bar-inner"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:448:            data-testid="match-results-bottom-bar-inner"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:457:              id="match-results-bottom-bar-left"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:458:              data-testid="match-results-bottom-bar-left"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:466:              id="match-results-bottom-bar-right"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:467:              data-testid="match-results-bottom-bar-right"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:478:                id="match-results-bottom-bar-cancel"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:479:                data-testid="match-results-bottom-bar-cancel"
frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:530:export default MatchResultsBottomBar;
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:532:                  aria-label="Loading match results"
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:582:                    defaultMessage="No match results available."
frontend/src/components/ImageModal.jsx:1028:                        defaultMessage="Click on an annotation to view its match results."
frontend/src/components/ImageModal.jsx:1044:                        `/react/match-results?taskId=${taskId}`,
frontend/src/pages/MatchResultsPage/helperFunctions.js:9:  const prospects = node.matchResults?.prospects?.[type];
frontend/src/pages/MatchResultsPage/helperFunctions.js:15:  const numberCandidatesRaw = node.matchResults?.numberCandidates;
frontend/src/pages/MatchResultsPage/helperFunctions.js:37:        node.matchResults?.queryAnnotation?.encounter?.id ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:39:        node.matchResults?.queryAnnotation?.encounter?.locationId ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:42:        node.matchResults?.queryAnnotation?.individual?.id ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:44:        node.matchResults?.queryAnnotation?.individual?.displayName ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:46:        node.matchResults?.queryAnnotation?.asset ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:48:        node.matchResults?.queryAnnotation?.asset?.url ?? null,
frontend/src/pages/MatchResultsPage/helperFunctions.js:49:      queryEncounterAnnotation: node.matchResults?.queryAnnotation
frontend/src/pages/MatchResultsPage/helperFunctions.js:51:            id: node.matchResults.queryAnnotation?.id,
frontend/src/pages/MatchResultsPage/helperFunctions.js:52:            x: node.matchResults.queryAnnotation?.x,
frontend/src/pages/MatchResultsPage/helperFunctions.js:53:            y: node.matchResults.queryAnnotation?.y,
frontend/src/pages/MatchResultsPage/helperFunctions.js:54:            width: node.matchResults.queryAnnotation?.width,
frontend/src/pages/MatchResultsPage/helperFunctions.js:55:            height: node.matchResults.queryAnnotation?.height,
frontend/src/pages/MatchResultsPage/helperFunctions.js:56:            theta: node.matchResults.queryAnnotation?.theta,
frontend/src/pages/MatchResultsPage/helperFunctions.js:57:            boundingBox: node.matchResults.queryAnnotation?.boundingBox,
frontend/src/pages/MatchResultsPage/helperFunctions.js:58:            isTrivial: node.matchResults.queryAnnotation?.isTrivial,
frontend/src/pages/MatchResultsPage/helperFunctions.js:59:            trivial: node.matchResults.queryAnnotation?.trivial,
frontend/src/components/icons/MatchResultIcon.jsx:3:export default function MatchResultIcon() {
frontend/src/components/home/PickUpWhereYouLeft.jsx:16:      ? `/react/match-results?taskId=${data?.latestMatchTask?.id}`

 succeeded in 1993ms:
src/main/java/org/ecocean/Annotation.java:16:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Annotation.java:17:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/Annotation.java:1812:    // we cant just detach the annots from match results, so we need
src/main/java/org/ecocean/Annotation.java:1814:    public long deleteMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1815:        return myShepherd.deleteMatchResults(this);
src/main/java/org/ecocean/Annotation.java:1818:    // similar as above for MatchResultProspects
src/main/java/org/ecocean/Annotation.java:1819:    public int deleteMatchResultProspects(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1820:        List<MatchResultProspect> mrps = myShepherd.getMatchResultProspects(this);
src/main/java/org/ecocean/Annotation.java:1823:        for (MatchResultProspect mrp : mrps) {
src/main/java/org/ecocean/Annotation.java:1825:            System.out.println("[DEBUG] (" + ct + ") ann.deleteMatchResultProspects() on id=" +
src/main/java/org/ecocean/Annotation.java:1853:        long nm = this.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/Annotation.java:1854:        int np = this.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/Annotation.java:1857:            " MatchResults, " + np + " MatchResultProspects, " + ne + " Embeddings on " + this);
src/main/java/org/ecocean/api/GenericObject.java:104:                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
src/main/java/org/ecocean/api/GenericObject.java:112:                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
src/main/java/org/ecocean/api/GenericObject.java:124:                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
src/main/java/org/ecocean/api/GenericObject.java:127:                            rtn.put("matchResultsRoot", mrJson);
src/main/java/org/ecocean/api/GenericObject.java:130:                            // this means we created on-the-fly some MatchResult(s) that need persisting
src/main/java/org/ecocean/Encounter.java:2526:        // NOTE: opposite logic in MatchResultProspect.isInProject()
src/main/java/org/ecocean/Embedding.java:18:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
src/main/java/org/ecocean/Embedding.java:392:                    "MatchResult persistence failed");
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:116:            // let's look for match results we can easily link for the user
src/main/java/org/ecocean/api/MarkedIndividualInfo.java:123:/* from matchResults.jsp ...
src/main/java/org/ecocean/servlet/AnnotationEdit.java:165:                        annot.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/AnnotationEdit.java:166:                        annot.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:107:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:108:                        ann.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:127:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:128:                        ann.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1889:                System.out.println("processCallbackIdentify() failed to create MatchResult on " +
src/main/java/org/ecocean/identity/IBEISIA.java:1893:                    "Creation of MatchResult upon task completion failed due to: " + ex);
src/main/java/org/ecocean/ia/Task.java:777:    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:778:        return myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:782:        List<MatchResult> all = myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:789:    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:790:        List<MatchResult> mrs = new ArrayList<MatchResult>();
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/ia/Task.java:801:                    System.out.println("[INFO] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:808:                    System.out.println("[ERROR] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:812:                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
src/main/java/org/ecocean/ia/Task.java:820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:828:        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
src/main/java/org/ecocean/ia/Task.java:844:            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
src/main/java/org/ecocean/ia/Task.java:847:            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
src/main/java/org/ecocean/ia/Task.java:848:               as vector results should have generated their MatchResult upon completion
src/main/java/org/ecocean/ia/Task.java:850:            MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/ia/Task.java:853:                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:855:                List<MatchResult> mrs = generateMatchResults(myShepherd);
src/main/java/org/ecocean/ia/Task.java:856:                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
src/main/java/org/ecocean/ia/Task.java:864:                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/ia/Task.java:871:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:19:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:20:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2809:        MatchResult mr = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2812:            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2821:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2824:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2829:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2835:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2838:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2843:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2848:    // faster deletion of all MatchResults associated with Annotation
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2849:    public long deleteMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2852:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2857:        System.out.println("[DEBUG] deleteMatchResults() deleted " + ct + " [" + (System.currentTimeMillis() - t) + "ms] on " + ann);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2861:    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2862:        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2865:        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2869:        if (c != null) all = new ArrayList<MatchResultProspect>(c);
src/main/java/org/ecocean/ia/MatchResult.java:34:public class MatchResult implements java.io.Serializable {
src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
src/main/java/org/ecocean/ia/MatchResult.java:50:    // number of MatchResultProspects [per type] to actually store (hotspotter
src/main/java/org/ecocean/ia/MatchResult.java:54:    public MatchResult() {
src/main/java/org/ecocean/ia/MatchResult.java:59:    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:64:    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:70:    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:77:    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:255:            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
src/main/java/org/ecocean/ia/MatchResult.java:266:    public Set<MatchResultProspect> getProspects() {
src/main/java/org/ecocean/ia/MatchResult.java:489:     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
src/main/java/org/ecocean/ia/MatchResult.java:539:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:546:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
src/main/java/org/ecocean/ia/MatchResult.java:548:        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:551:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:564:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
src/main/java/org/ecocean/ia/MatchResult.java:646:        String s = "MatchResult " + id;
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:21: * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:22: * Replaces the previous in-{@link MatchResult}-constructor PairX calls
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:28: *       {@link #loadDtos(String)} loads the MatchResult, walks its
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:42: * same MatchResult still get processed.</p>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:55:     * For each prospect of the named MatchResult that lacks an
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:62:    public int enrichMatchResult(String matchResultId) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:99:     * Phase A: load all PairxDtos for the given MatchResult under one
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:108:            MatchResult mr = shep.getMatchResult(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:122:            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:125:                for (MatchResultProspect prospect : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:128:                    // same MatchResult under operator-driven re-fire).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:133:                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:169:        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:170:            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177:        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:192:     * {@code MatchResult.createInspectionPairxAsset} sent, with the
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:207:        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:214:     * within the MatchResult. Returns true on successful attach, false
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:223:            MatchResult mr = shep.getMatchResult(dto.matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:228:            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:261:     * Find a prospect in the given MatchResult by (annotationId, scoreType).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:264:    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:267:        for (MatchResultProspect p : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:15:public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
src/main/java/org/ecocean/ia/MatchResultProspect.java:20:    private MatchResult matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:22:    public MatchResultProspect() {}
src/main/java/org/ecocean/ia/MatchResultProspect.java:24:    public MatchResultProspect(Annotation ann) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:29:    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:40:     * prospects after the MatchResult has been persisted (empty-match-
src/main/java/org/ecocean/ia/MatchResultProspect.java:85:        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResultProspect.java:97:    @Override public int compareTo(MatchResultProspect other) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:            // MatchResult + prospects are already persisted with
src/main/java/org/ecocean/ia/MlServiceProcessor.java:566:     * MatchResult attached to a child of {@code matchTaskId}. Runs
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        List<String> mrIds = collectMatchResultIds(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:579:                enricher.enrichMatchResult(mrId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:589:     * Open a short Shepherd, list MatchResult IDs attached to children
src/main/java/org/ecocean/ia/MlServiceProcessor.java:593:    private List<String> collectMatchResultIds(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:596:        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:608:                    List<MatchResult> mrs = shep.getMatchResults(child);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                    for (MatchResult mr : mrs) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:621:                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
src/main/java/org/ecocean/servlet/ReactAppServlet.java:133:     * without an extension in the last segment ({@code /react/match-results},

 succeeded in 4700ms:
./docs/plans/2026-05-19-c14-codex-review.md:247:+	  blanking the React match-results page.
./docs/plans/2026-05-19-c14-codex-review.md:349:    62		  blanking the React match-results page.
./docs/plans/2026-05-19-c14-codex-review.md:930:frontend/src/AuthenticatedSwitch.jsx:86:            <Route path="/match-results" element={<MatchResults />} />
./docs/plans/2026-05-19-c14-codex-review.md:1192:I’d add servlet/integration coverage for: `GET /react/match-results -> 200`, `GET /react-something -> 404`, `GET /api/missing -> 404`, `POST /api/missing -> 404 not 405`, and a missing React asset such as `/react/static/js/missing.js` preserving 404.
./docs/plans/2026-05-19-c14-codex-review.md:1207:I’d add servlet/integration coverage for: `GET /react/match-results -> 200`, `GET /react-something -> 404`, `GET /api/missing -> 404`, `POST /api/missing -> 404 not 405`, and a missing React asset such as `/react/static/js/missing.js` preserving 404.
./src/main/webapp/WEB-INF/web.xml:62:	  blanking the React match-results page.
./frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:648:      expect.stringContaining("/api/v3/tasks/task-abc/match-results"),
./frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:188:const renderComponent = (url = "/match-results?taskId=task-1") =>
./frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:220:    renderComponent("/match-results");
./src/test/java/org/ecocean/servlet/ReactAppServletTest.java:30:        assertEquals("/react/match-results",
./src/test/java/org/ecocean/servlet/ReactAppServletTest.java:31:            ReactAppServlet.stripContextPath(req, "/react/match-results"));
./src/test/java/org/ecocean/servlet/ReactAppServletTest.java:36:        assertEquals("/react/match-results",
./src/test/java/org/ecocean/servlet/ReactAppServletTest.java:38:                "/wildbook/react/match-results"));
./src/test/java/org/ecocean/servlet/ReactAppServletTest.java:59:        assertFalse(ReactAppServlet.looksLikeStaticAsset("/react/match-results"));
./src/main/webapp/encounters/biologicalSamples.jsp:1949:        wildbook.openInTab('../react/match-results?taskId=' + xhr.responseJSON.taskId);
./frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:425:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
./frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:447:          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:97:    return <FullScreenLoader data-testid="match-results-loader" />;
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:105:      id="match-results-page"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:106:      data-testid="match-results-page"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:122:        <div data-testid="match-results-bottom-bar">
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:134:          data-testid="match-results-bottom-bar-spacer"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:140:        id="match-results-header"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:141:        data-testid="match-results-header"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:144:          <h2 id="match-results-title" data-testid="match-results-title">
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:153:            id="match-results-instructions-trigger"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:154:            data-testid="match-results-instructions-trigger"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:158:              data-testid="match-results-instructions-icon"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:166:        id="match-results-toolbar"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:167:        data-testid="match-results-toolbar"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:171:          id="match-results-viewmode"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:172:          data-testid="match-results-viewmode"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:177:            id="match-results-viewmode-individual"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:178:            data-testid="match-results-viewmode-individual"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:202:            id="match-results-viewmode-image"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:203:            data-testid="match-results-viewmode-image"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:228:          id="match-results-controls"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:229:          data-testid="match-results-controls"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:233:            id="match-results-num-results-group"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:234:            data-testid="match-results-num-results-group"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:241:                data-testid="match-results-num-results-hint"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:252:              id="match-results-num-results-wrapper"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:253:              data-testid="match-results-num-results-wrapper"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:256:                id="match-results-num-results-input"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:257:                data-testid="match-results-num-results-input"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:282:                  id="match-results-num-results-apply"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:283:                  data-testid="match-results-num-results-apply"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:311:            id="match-results-project-group"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:312:            data-testid="match-results-project-group"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:320:              id="match-results-project-select-wrapper"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:321:              data-testid="match-results-project-select-wrapper"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:355:            id="match-results-filter-trigger"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:356:            data-testid="match-results-filter-trigger"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:365:            <FilterIcon data-testid="match-results-filter-icon" />
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:370:      <div id="match-results-content" data-testid="match-results-content">
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:374:            id="match-results-empty"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:375:            data-testid="match-results-empty"
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:383:          <div id="match-results-sections" data-testid="match-results-sections">
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:388:                  id={`match-results-section-${store.viewMode}-${taskId}`}
./frontend/src/pages/MatchResultsPage/MatchResults.jsx:389:                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
./docs/plans/2026-05-09-ml-service-migration-v2.md:168:- `frontend/src/pages/Encounter/stores/EncounterStore.js`: match-results / annotation status checks recognize `complete-mlservice`.
./frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js:79:    expect(latestMatchItem).toHaveTextContent("/react/match-results?taskId=123");
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:441:          id="match-results-bottom-bar"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:442:          data-testid="match-results-bottom-bar"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:447:            id="match-results-bottom-bar-inner"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:448:            data-testid="match-results-bottom-bar-inner"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:457:              id="match-results-bottom-bar-left"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:458:              data-testid="match-results-bottom-bar-left"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:466:              id="match-results-bottom-bar-right"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:467:              data-testid="match-results-bottom-bar-right"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:478:                id="match-results-bottom-bar-cancel"
./frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx:479:                data-testid="match-results-bottom-bar-cancel"
./frontend/src/components/ImageModal.jsx:1044:                        `/react/match-results?taskId=${taskId}`,
./frontend/src/AuthenticatedSwitch.jsx:86:            <Route path="/match-results" element={<MatchResults />} />
./src/main/webapp/iaResults.jsp:2012:	let reloadURL = "../react/match-results?taskId="+taskId;
./frontend/src/components/home/PickUpWhereYouLeft.jsx:16:      ? `/react/match-results?taskId=${data?.latestMatchTask?.id}`
./src/test/java/org/ecocean/api/TaskMatchResults.java:96:    // basically tests api path without /match-results
./src/test/java/org/ecocean/api/TaskMatchResults.java:127:        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
./src/test/java/org/ecocean/api/TaskMatchResults.java:155:        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
./frontend/src/pages/Encounter/MatchCriteria.jsx:133:                  const url = `/react/match-results?taskId=${result?.data?.taskId}`;
./frontend/src/pages/Encounter/ImageCard.jsx:707:                  const url = `/react/match-results?taskId=${encodeURIComponent(taskId)}`;
./frontend/src/pages/Encounter/ImageCard.jsx:748:                    const url = `/react/match-results?taskId=${encodeURIComponent(selectedAnnotation.iaTaskId)}`;
./frontend/src/pages/BulkImport/BulkImportTask.jsx:216:          const link = `/react/match-results?taskId=${arr[0]}`;
./frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js:215:        "/react/match-results?taskId=t123",
./src/main/webapp/javascript/ia.IBEIS.js:104:                //wildbook.openInTab('../react/match-results?taskId=' + iaStatus.taskId);
./src/main/webapp/javascript/ia.IBEIS.js:113:                wildbook.openInTab('../react/match-results?taskId=' + iaStatus.taskId);
./frontend/src/__tests__/pages/Encounter/ImageModal.test.js:228:      "/react/match-results?taskId=task-123",
./frontend/src/__tests__/pages/Encounter/ImageCard.test.js:305:    expect(url).toContain("/react/match-results?taskId=TASK-99");
./frontend/src/__tests__/pages/Encounter/ImageCard.test.js:374:    expect(url).toContain("/react/match-results?taskId=TASK-FR-1");
./src/main/webapp/projects/project.jsp:335:  projectHTML +=  '     <button id="disabled-match-results-encId-'+encounterId+'" style="display: none;" class="disabled-btn visitResultsButton proj-action-btn">'+txt.matchResults+'</button>';
./src/main/webapp/projects/project.jsp:336:  projectHTML +=  '     <button id="match-results-encId-'+encounterId+'" class="visitResultsButton proj-action-btn" onclick="openIaResultsOptions(this)">'+txt.matchResults+'</button>';
./src/main/webapp/projects/project.jsp:445:    $("#match-results-encId-"+currentEncounterId).html(txt.matchResults);
./src/main/webapp/projects/project.jsp:487:  $("#disabled-match-results-encId-" + encId).hide();
./src/main/webapp/projects/project.jsp:488:  $("#match-results-encId-" + encId).show();
./src/main/webapp/projects/project.jsp:492:  $("#match-results-encId-" + encId).hide();
./src/main/webapp/projects/project.jsp:493:  $("#disabled-match-results-encId-" + encId).show();
./src/main/webapp/projects/project.jsp:553:	  window.open('/react/match-results?taskId='+taskId+'&projectIdPrefix='+encodeURIComponent(projIdPrefix), "_blank");
./src/main/java/org/ecocean/api/GenericObject.java:104:                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
./src/main/java/org/ecocean/servlet/ReactAppServlet.java:133:     * without an extension in the last segment ({@code /react/match-results},

codex
The route is in `AuthenticatedSwitch.jsx` and the frontend store is already pointing at `/api/v3/tasks/{taskId}/match-results`. I’ve also found the backend handler in `GenericObject`, so the next step is to inspect those paths and the store’s normalization logic rather than keep searching broadly.
exec
/bin/bash -lc "sed -n '1,140p' frontend/src/AuthenticatedSwitch.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,260p' frontend/src/pages/MatchResultsPage/MatchResults.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,360p' frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '80,150p' src/main/java/org/ecocean/api/GenericObject.java" in /mnt/c/Wildbook-clean2
 succeeded in 224ms:
import React, { lazy, Suspense } from "react";

import { Routes, Route } from "react-router-dom";
import NotFound from "./pages/errorPages/NotFound";
import AuthenticatedAppHeader from "./components/AuthenticatedAppHeader";
import Footer from "./components/Footer";
import LoadingScreen from "./components/LoadingScreen";
import useGetMe from "./models/auth/users/useGetMe";
const HowToPhotograph = lazy(() => import("./pages/HowToPhotograph"));
const AboutUs = lazy(() => import("./pages/AboutUs"));

// Lazy load pages
const Login = lazy(() => import("./pages/Login"));
const Home = lazy(() => import("./pages/Home"));
const EncounterSearch = lazy(
  () => import("./pages/SearchPages/EncounterSearch"),
);
const AdminLogs = lazy(() => import("./pages/AdminLogs"));
const ReportEncounter = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportEncounter"),
);
const ReportConfirm = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportConfirm"),
);
const ProjectList = lazy(() => import("./pages/ProjectList"));
const ManualAnnotation = lazy(() => import("./pages/ManualAnnotation"));
const EditAnnotation = lazy(() => import("./pages/EditAnnotation"));

const BulkImport = lazy(() => import("./pages/BulkImport/BulkImport"));
const BulkImportTask = lazy(() => import("./pages/BulkImport/BulkImportTask"));
const MatchResults = lazy(
  () => import("./pages/MatchResultsPage/MatchResults"),
);

const Encounter = lazy(() => import("./pages/Encounter/Encounter"));
const Citation = lazy(() => import("./pages/Citation"));
const PoliciesAndData = lazy(
  () => import("./pages/PoliciesAndData/PoliciesAndData"),
);

export default function AuthenticatedSwitch({
  showclassicsubmit,
  showClassicEncounterSearch,
  showHowToPhotograph,
  siteSettingsLoading,
}) {
  const { data } = useGetMe();
  const username = data?.username;
  const avatar =
    data?.imageURL || `${process.env.PUBLIC_URL}/images/Avatar.png`;
  const [header, setHeader] = React.useState(true);

  return (
    <div className="d-flex flex-column min-vh-100">
      {/* Header */}
      <div
        id="header"
        className="position-fixed top-0 mx-auto w-100"
        style={{
          zIndex: "100",
          height: "50px",
          backgroundColor: "#303336",
        }}
      >
        <AuthenticatedAppHeader
          username={username}
          avatar={avatar}
          showclassicsubmit={showclassicsubmit}
          showClassicEncounterSearch={showClassicEncounterSearch}
          showHowToPhotograph={showHowToPhotograph}
        />
      </div>

      {/* Main Content */}
      <div
        id="main-content"
        className="flex-grow-1 d-flex justify-content-center"
        style={{
          boxSizing: "border-box",
          overflow: "hidden",
          paddingTop: header ? "48px" : "0",
        }}
      >
        <Suspense fallback={<div>Loading...</div>}>
          <Routes>
            <Route path="/match-results" element={<MatchResults />} />
            <Route path="/citation" element={<Citation />} />
            <Route path="/policies-and-data" element={<PoliciesAndData />} />
            <Route
              path="/how-to-photograph"
              element={
                siteSettingsLoading ? (
                  <LoadingScreen />
                ) : showHowToPhotograph ? (
                  <HowToPhotograph />
                ) : (
                  <NotFound setHeader={setHeader} />
                )
              }
            />

            <Route path="/about-us" element={<AboutUs />} />
            <Route path="/projects/overview" element={<ProjectList />} />
            <Route path="/home" element={<Home />} />
            <Route path="/report" element={<ReportEncounter />} />
            <Route path="/reportConfirm" element={<ReportConfirm />} />
            <Route path="/encounter" element={<Encounter />} />

            <Route path="/encounter-search" element={<EncounterSearch />} />
            <Route path="/admin/logs" element={<AdminLogs />} />
            <Route path="/manual-annotation" element={<ManualAnnotation />} />
            <Route path="/edit-annotation" element={<EditAnnotation />} />
            <Route path="/bulk-import" element={<BulkImport />} />
            <Route path="/bulk-import-task" element={<BulkImportTask />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<Home />} />
            <Route path="*" element={<NotFound setHeader={setHeader} />} />
          </Routes>
        </Suspense>
      </div>

      <Footer />
    </div>
  );
}

 succeeded in 218ms:
import React, { useMemo, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Form } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./stores/matchResultsStore";
import MatchProspectTable from "./components/MatchProspectTable";
import MatchResultsBottomBar from "./components/MatchResultsBottomBar";
import { useSearchParams } from "react-router-dom";
import { useSiteSettings } from "../../SiteSettingsContext";
import FullScreenLoader from "../../components/FullScreenLoader";
import InstructionsModal from "./components/InstructionsModal";
import InfoIcon from "./icons/InfoIcon";
import FilterIcon from "./icons/FilterIcon";
import MatchCriteriaDrawer from "./components/MatchCriteriaDrawer";
import MultiSelectWithCheckbox from "../../components/MultiSelectWithCheckbox";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";
import { MAX_NUM_RESULTS } from "./constants";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);
  const [params, setParams] = useSearchParams();
  const taskId = params.get("taskId");
  const projectIdPrefix = params.get("projectIdPrefix");
  const { data, isLoading: siteSettingsLoading } = useSiteSettings();

  // Stabilize projectsForUser reference to prevent unnecessary effect re-renders
  const projectsForUser = React.useMemo(
    () => data?.projectsForUser ?? {},
    [data?.projectsForUser],
  );
  const identificationRemarks = React.useMemo(
    () => data?.identificationRemarks ?? [],
    [data?.identificationRemarks],
  );

  const [filterVisible, setFilterVisible] = React.useState(false);
  const [isInputFocused, setIsInputFocused] = React.useState(false);

  const projectOptions = useMemo(() => {
    return Object.entries(projectsForUser).map(([key, value]) => ({
      value: key,
      label: value?.name || key,
    }));
  }, [projectsForUser]);

  useEffect(() => {
    if (taskId) {
      let initialProjectIds = [];

      if (projectIdPrefix) {
        if (siteSettingsLoading) return;

        const match = Object.entries(projectsForUser).find(
          ([, p]) => p?.prefix === projectIdPrefix,
        );
        if (match) {
          initialProjectIds = [match[0]];
        }
      }

      store.setTaskId(taskId);
      store.setProjectNames(initialProjectIds, { fetch: false });
      store.fetchMatchResults();
    } else {
      store.setTaskId(null);
      store.setProjectNames([], { fetch: false });
      store.clearResults();
    }
  }, [taskId, projectIdPrefix, projectsForUser, siteSettingsLoading]);

  useEffect(() => {
    if (!taskId || !store.shouldPoll) return;

    let cancelled = false;

    const scheduleNext = async () => {
      if (cancelled) return;

      await store.fetchMatchResults({ silent: true });

      if (!cancelled && store.shouldPoll) {
        setTimeout(scheduleNext, 5000);
      }
    };

    scheduleNext();

    return () => {
      cancelled = true;
    };
  }, [taskId, store.shouldPoll]);

  if (store.loading) {
    return <FullScreenLoader data-testid="match-results-loader" />;
  }

  const showEmptyState = !store.hasDisplaySections;

  return (
    <Container
      className="mt-2 mb-5"
      id="match-results-page"
      data-testid="match-results-page"
    >
      <InstructionsModal
        show={instructionsVisible}
        onHide={() => setInstructionsVisible(false)}
        taskId={taskId}
        themeColor={themeColor}
      />

      <MatchCriteriaDrawer
        show={filterVisible}
        onHide={() => setFilterVisible(false)}
        filter={store.matchingSetFilter}
      />

      {store.hasResults && store.encounterId && (
        <div data-testid="match-results-bottom-bar">
          <MatchResultsBottomBar
            store={store}
            themeColor={themeColor}
            identificationRemarks={identificationRemarks}
          />
        </div>
      )}

      {store.hasResults && store.encounterId && (
        <div
          style={{ height: "70px" }}
          data-testid="match-results-bottom-bar-spacer"
        />
      )}

      <div
        className="d-flex flex-row justify-content-between align-items-center mb-3"
        id="match-results-header"
        data-testid="match-results-header"
      >
        <div className="d-flex flex-row align-items-center">
          <h2 id="match-results-title" data-testid="match-results-title">
            <FormattedMessage id="MATCH_RESULT" />
          </h2>
        </div>

        <span>
          <div
            title="Match Page Instructions"
            style={{ display: "inline-flex", cursor: "pointer" }}
            id="match-results-instructions-trigger"
            data-testid="match-results-instructions-trigger"
          >
            <InfoIcon
              onClick={() => setInstructionsVisible(true)}
              data-testid="match-results-instructions-icon"
            />
          </div>
        </span>
      </div>

      <div
        className="d-flex align-items-center flex-wrap mb-3"
        id="match-results-toolbar"
        data-testid="match-results-toolbar"
      >
        <div
          className="d-flex align-items-center"
          id="match-results-viewmode"
          data-testid="match-results-viewmode"
        >
          <button
            className="me-2"
            type="button"
            id="match-results-viewmode-individual"
            data-testid="match-results-viewmode-individual"
            style={{
              borderRadius: "35px",
              backgroundColor:
                store.viewMode === "individual"
                  ? themeColor.primaryColors.primary500
                  : themeColor.primaryColors.primary50,
              border: "none",
              padding: "5px 10px",
              color:
                store.viewMode === "individual"
                  ? "white"
                  : themeColor.primaryColors.primary500,
            }}
            onClick={() => {
              store.setViewMode("individual");
              store.resetSelectionToQuery();
            }}
          >
            <FormattedMessage id="INDIVIDUAL_SCORE" />
          </button>

          <button
            type="button"
            id="match-results-viewmode-image"
            data-testid="match-results-viewmode-image"
            style={{
              borderRadius: "35px",
              backgroundColor:
                store.viewMode === "image"
                  ? themeColor.primaryColors.primary500
                  : themeColor.primaryColors.primary50,
              padding: "5px 10px",
              border: "none",
              color:
                store.viewMode === "image"
                  ? "white"
                  : themeColor.primaryColors.primary700,
            }}
            onClick={() => {
              store.setViewMode("image");
              store.resetSelectionToQuery();
            }}
          >
            <FormattedMessage id="IMAGE_SCORE" />
          </button>
        </div>

        <div
          className="ms-auto d-flex align-items-center flex-wrap"
          id="match-results-controls"
          data-testid="match-results-controls"
        >
          <Form.Group
            className="d-flex align-items-center me-3 mb-2 mb-sm-0"
            id="match-results-num-results-group"
            data-testid="match-results-num-results-group"
          >
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage id="NUMBER_OF_RESULTS" />
              <span
                className="ms-1 text-muted"
                style={{ fontSize: "0.75rem" }}
                data-testid="match-results-num-results-hint"
              >
                <FormattedMessage
                  id="NUMBER_OF_RESULTS_MAX_HINT"
                  values={{ max: MAX_NUM_RESULTS }}
                />
              </span>
            </Form.Label>

            <div
              style={{ position: "relative", width: "80px" }}
              id="match-results-num-results-wrapper"
              data-testid="match-results-num-results-wrapper"
            >
              <Form.Control
                id="match-results-num-results-input"
                data-testid="match-results-num-results-input"
                type="text"
                size="sm"
                value={store.numResults}

 succeeded in 254ms:
import { makeAutoObservable } from "mobx";
import axios from "axios";
import { toast } from "react-toastify";
import {
  MAX_ROWS_PER_COLUMN,
  MAX_NUM_RESULTS,
  DEFAULT_NUM_RESULTS,
} from "../constants";
import {
  getAllAnnot,
  getAllIndiv,
  isMatchTaskStillRunning,
  hasMatchTaskError,
  isMatchTaskTerminal,
} from "../helperFunctions";

export default class MatchResultsStore {
  _viewMode = "individual"; // "individual" | "image"
  _encounterId = "";
  _annotResults = [];
  _indivResults = [];
  _encounterLocationId = "";
  _useNextIndividualName = false;
  _statusOverall = "";
  _matchingSetFilter = {};
  _individualId = null;
  _individualDisplayName = null;
  _projectNames = [];
  _numResults = DEFAULT_NUM_RESULTS;
  _selectedMatchImageUrlByAlgo = new Map();
  _selectedMatch = [];
  _taskId = null;
  _newIndividualName = "";

  // raw data from API, before grouping / processing
  _rawAnnots = [];
  _rawIndivs = [];
  _processedAnnots = [];
  _processedIndivs = [];

  _loading = false;
  _matchRequestLoading = false;
  _matchRequestError = null;
  _hasResults = false;
  _taskStillRunning = false;
  _taskHasError = false;
  _taskIsTerminal = false;
  _rootStillRunning = false;
  _rootHasError = false;
  _currentRequestId = null;

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  _findBestQuerySource() {
    const candidates = [
      ...(Array.isArray(this._indivResults) ? this._indivResults : []),
      ...(Array.isArray(this._annotResults) ? this._annotResults : []),
    ];

    if (candidates.length === 0) return null;

    const withEncounterId = candidates.find((item) => item?.queryEncounterId);
    if (withEncounterId) return withEncounterId;

    const withQueryAnnotationEncounter = candidates.find(
      (item) => item?.queryEncounterAnnotation?.encounter?.id,
    );
    if (withQueryAnnotationEncounter) return withQueryAnnotationEncounter;

    const withSomeQueryContext = candidates.find(
      (item) =>
        item?.queryEncounterImageAsset ||
        item?.queryEncounterImageUrl ||
        item?.queryEncounterAnnotation ||
        item?.matchingSetFilter ||
        item?.queryIndividualId,
    );
    if (withSomeQueryContext) return withSomeQueryContext;

    return candidates[0];
  }

  loadData(results, { preserveSelection = false } = {}) {
    const root = results?.matchResultsRoot;

    this._annotResults = getAllAnnot(root);
    this._indivResults = getAllIndiv(root);
    this._taskStillRunning = isMatchTaskStillRunning(root);
    this._taskHasError = hasMatchTaskError(root);
    this._taskIsTerminal = isMatchTaskTerminal(root);

    const rootHasChildren =
      Array.isArray(root?.children) && root.children.length > 0;
    const rootHasMatchResults = !!root?.matchResults;

    this._rootStillRunning =
      !rootHasChildren &&
      !rootHasMatchResults &&
      !!root?.statusOverall &&
      root.statusOverall !== "completed" &&
      root.statusOverall !== "error";

    this._rootHasError =
      !rootHasChildren &&
      !rootHasMatchResults &&
      root?.statusOverall === "error";

    const hasAnyResults =
      (Array.isArray(this._annotResults) && this._annotResults.length > 0) ||
      (Array.isArray(this._indivResults) && this._indivResults.length > 0);

    if (!hasAnyResults) {
      this._rawAnnots = [];
      this._rawIndivs = [];
      this._processedAnnots = [];
      this._processedIndivs = [];
      this._encounterId = null;
      this._matchingSetFilter = {};
      this._individualId = null;
      this._individualDisplayName = null;
      this._hasResults = false;
      this._encounterLocationId = "";
      this._statusOverall = root?.statusOverall || "";

      if (!preserveSelection) {
        this.resetSelectionToQuery();
      }
      return;
    }

    if (!this._annotResults || this._annotResults.length === 0) {
      this._viewMode = "individual";
    }
    if (!this._indivResults || this._indivResults.length === 0) {
      this._viewMode = "image";
    }

    const querySource = this._findBestQuerySource();
    if (!querySource) return;

    this._encounterId =
      querySource.queryEncounterId ||
      querySource.queryEncounterAnnotation?.encounter?.id ||
      null;

    this._encounterLocationId = querySource.encounterLocationId || "";
    this._matchingSetFilter = querySource.matchingSetFilter || {};
    this._individualId = querySource.queryIndividualId || null;
    this._individualDisplayName =
      querySource.queryIndividualDisplayName || null;
    this._statusOverall = querySource.taskStatusOverall || "";

    this._rawAnnots = Array.isArray(this._annotResults)
      ? this._annotResults
      : [];
    this._rawIndivs = Array.isArray(this._indivResults)
      ? this._indivResults
      : [];
    this._hasResults = this._rawAnnots.length > 0 || this._rawIndivs.length > 0;
    this._processedAnnots = this._processData(this._rawAnnots);
    this._processedIndivs = this._processData(this._rawIndivs);

    if (!preserveSelection) {
      this.resetSelectionToQuery();
    }
  }

  _processData(rawData) {
    // 1. group by task
    const groupedByTask = new Map();
    for (const item of rawData) {
      const taskId = item.taskId || "unknown-task";
      if (!groupedByTask.has(taskId)) groupedByTask.set(taskId, []);
      groupedByTask.get(taskId).push(item);
    }

    //2. divide to columns
    const sections = [];

    for (const [taskId, items] of groupedByTask) {
      const sorted = items;

      const columns = [];
      for (let i = 0; i < sorted.length; i += MAX_ROWS_PER_COLUMN) {
        const columnData = sorted
          .slice(i, i + MAX_ROWS_PER_COLUMN)
          .map((data, index) => ({
            ...data,
            displayIndex: i + index + 1,
          }));
        columns.push(columnData);
      }

      const first =
        sorted.find(
          (item) =>
            item?.queryEncounterId ||
            item?.queryEncounterImageAsset ||
            item?.queryEncounterImageUrl ||
            item?.queryEncounterAnnotation ||
            item?.methodName ||
            item?.methodDescription ||
            item?.algorithm,
        ) ||
        sorted[0] ||
        {};

      sections.push({
        taskId,
        columns,
        metadata: {
          numCandidates: first.numberCandidates ?? "-",
          date: first.date,
          queryImageUrl:
            first.queryEncounterImageAsset?.url || first.queryEncounterImageUrl,
          queryEncounterImageAsset: first.queryEncounterImageAsset,
          queryEncounterAnnotation: first.queryEncounterAnnotation,
          methodName: first.methodName,
          methodDescription: first.methodDescription,
          taskStatus: first.taskStatus,
          taskStatusOverall: first.taskStatusOverall,
          algorithm: first.algorithm,
          emptyStateType: first.emptyStateType ?? null,
          errors: first.errors ?? null,
        },
      });
    }

    return sections;
  }

  clearResults() {
    this._annotResults = [];
    this._indivResults = [];
    this._rawAnnots = [];
    this._rawIndivs = [];
    this._processedAnnots = [];
    this._processedIndivs = [];
    this._encounterId = null;
    this._encounterLocationId = "";
    this._matchingSetFilter = {};
    this._individualId = null;
    this._individualDisplayName = null;
    this._statusOverall = "";
    this._viewMode = "individual";
    this._newIndividualName = "";
    this._hasResults = false;
    this._taskStillRunning = false;
    this._taskHasError = false;
    this._taskIsTerminal = false;
    this._rootStillRunning = false;
    this._rootHasError = false;

    this.resetSelectionToQuery();
  }

  // --- computed data for UI ---

  get processedAnnots() {
    return this._processedAnnots;
  }

  get processedIndivs() {
    return this._processedIndivs;
  }

  get currentViewData() {
    return this._viewMode === "individual"
      ? this._processedIndivs
      : this._processedAnnots;
  }

  get viewMode() {
    return this._viewMode;
  }

  get taskStillRunning() {
    return this._taskStillRunning;
  }

  get taskHasError() {
    return this._taskHasError;
  }

  get taskIsTerminal() {
    return this._taskIsTerminal;
  }

  get rootStillRunning() {
    return this._rootStillRunning;
  }

  get rootHasError() {
    return this._rootHasError;
  }

  get shouldPoll() {
    return !!this._taskId && this._taskStillRunning;
  }

  get hasDisplaySections() {
    return this.currentViewData.length > 0;
  }

  get encounterId() {
    return this._encounterId;
  }

  get encounterLocationId() {
    return this._encounterLocationId;
  }

  get matchingSetFilter() {
    return this._matchingSetFilter;
  }

  get individualId() {
    return this._individualId;
  }

  get individualDisplayName() {
    return this._individualDisplayName;
  }

  get projectNames() {
    return this._projectNames;
  }

  get numResults() {
    return this._numResults;
  }

  get loading() {
    return this._loading;
  }

  get matchRequestLoading() {
    return this._matchRequestLoading;
  }

  get matchRequestError() {
    return this._matchRequestError;
  }

  get hasResults() {
    return this._hasResults;
  }

  get newIndividualName() {
    return this._newIndividualName;
  }

  get taskId() {
    return this._taskId;
  }

  get selectedMatch() {
    return this._selectedMatch;

 succeeded in 261ms:
                                        jann.put("height", (int)ma.getHeight());
                                    } else {
                                        // basically if we have more than one feature, only one wins
                                        if (ft.getParameters() != null) jann = ft.getParameters();
                                    }
                                }
                            }
                            Encounter enc = ann.findEncounter(myShepherd);
                            if (enc != null) {
                                jann.put("encounterId", enc.getId());
                                jann.put("encounterTaxonomy", enc.getTaxonomyString());
                            }
                            jann.put("id", ann.getId());
                            janns.put(jann);
                        }
                        rtn.put("annotations", janns);
                    }
                }
                break;
            case "tasks":
                if (currentUser == null) {
                    rtn.put("statusCode", 401);
                    rtn.put("error", "access denied");
                } else {
                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
                        Task task = myShepherd.getTask(args[1]);
                        if (task == null) {
                            rtn.put("statusCode", 404);
                            rtn.put("error", "not found");
                        } else {
                            // right now we replicate legacy functionality and allow access to anyone
                            // based on task id only, rather than blocking based on task.canUserAccess()
                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
                            Set<String> projectIds = null;
                            String[] pvals = request.getParameterValues("projectId");
                            if ((pvals != null) && (pvals.length > 0))
                                projectIds = new HashSet<String>(Arrays.asList(
                                    request.getParameterValues("projectId")));
                            try {
                                // note: negative size means all of them (no cutoff)
                                prospectsSize = Integer.parseInt(request.getParameter(
                                    "prospectsSize"));
                            } catch (NumberFormatException ex) {}
                            rtn.put("prospectsSize", prospectsSize);
                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
                                myShepherd);
                            rtn.put("projectIds", projectIds);
                            rtn.put("matchResultsRoot", mrJson);
                            rtn.put("success", true);
                            rtn.put("statusCode", 200);
                            // this means we created on-the-fly some MatchResult(s) that need persisting
                            commitShepherd = (mrJson != null) &&
                                mrJson.optBoolean("_commitShepherd", false);
                            if (commitShepherd) myShepherd.commitDBTransaction();
                        }
                    } else {
                        throw new ApiException("invalid tasks operation");
                    }
                }
                break;
            default:
                throw new ApiException("bad class");
            }
        } catch (ApiException apiEx) {
            rtn.put("statusCode", 400);
            rtn.put("errors", apiEx.getErrors());
            rtn.put("debug", apiEx.toString());
        } finally {
            if (commitShepherd) {
                myShepherd.closeDBTransaction();
            } else {

 succeeded in 15136ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/
migrate-ml-service-v2

exec
/bin/bash -lc "sed -n '260,620p' frontend/src/pages/MatchResultsPage/MatchResults.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '360,760p' frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,320p' frontend/src/pages/MatchResultsPage/helperFunctions.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx" in /mnt/c/Wildbook-clean2
 succeeded in 282ms:
                value={store.numResults}
                onChange={(e) => {
                  const val = e.target.value;
                  if (/^\d*$/.test(val)) {
                    store.setNumResults(val === "" ? 1 : Number(val));
                  }
                }}
                onFocus={() => setIsInputFocused(true)}
                onBlur={() => setIsInputFocused(false)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    store.fetchMatchResults();
                  }
                }}
                style={{
                  width: "100%",
                  paddingRight: "30px",
                }}
              />

              {isInputFocused && (
                <button
                  id="match-results-num-results-apply"
                  data-testid="match-results-num-results-apply"
                  type="button"
                  onClick={() => store.fetchMatchResults()}
                  onMouseDown={(e) => e.preventDefault()}
                  style={{
                    position: "absolute",
                    right: "4px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    border: "none",
                    background: "transparent",
                    cursor: "pointer",
                    padding: "0 4px",
                    fontSize: "16px",
                    color: themeColor.primaryColors.primary500,
                    lineHeight: "1",
                  }}
                  title="Apply changes"
                  aria-label="Apply number of results"
                >
                  ✓
                </button>
              )}
            </div>
          </Form.Group>

          <Form.Group
            className="d-flex align-items-center me-3 mb-2 mb-sm-0"
            id="match-results-project-group"
            data-testid="match-results-project-group"
          >
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage id="PROJECT" defaultMessage="Project" />
            </Form.Label>

            <div
              style={{ minWidth: "220px", maxWidth: "400px" }}
              id="match-results-project-select-wrapper"
              data-testid="match-results-project-select-wrapper"
            >
              <ContainerWithSpinner loading={siteSettingsLoading}>
                <MultiSelectWithCheckbox
                  options={projectOptions}
                  value={store.projectNames || []}
                  placeholder={
                    <FormattedMessage
                      id="SELECT_PROJECTS"
                      defaultMessage="Select projects"
                    />
                  }
                  onChangeCommitted={(projectIds) => {
                    store.setProjectNames(projectIds);

                    if (!projectIds || projectIds.length === 0) {
                      const next = new URLSearchParams(params);
                      next.delete("projectIdPrefix");
                      setParams(next, { replace: true });
                    }
                  }}
                  style={{ width: "100%" }}
                />
              </ContainerWithSpinner>
            </div>
          </Form.Group>

          <div
            title="Match Criteria"
            style={{
              display: "inline-flex",
              cursor: "pointer",
              marginRight: "10px",
            }}
            id="match-results-filter-trigger"
            data-testid="match-results-filter-trigger"
            onClick={() => setFilterVisible(true)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") setFilterVisible(true);
            }}
            aria-label="Open match criteria"
          >
            <FilterIcon data-testid="match-results-filter-icon" />
          </div>
        </div>
      </div>

      <div id="match-results-content" data-testid="match-results-content">
        {showEmptyState ? (
          <p
            className="mt-3"
            id="match-results-empty"
            data-testid="match-results-empty"
          >
            <FormattedMessage
              id="NO_MATCH_RESULT"
              defaultMessage="No match results available."
            />
          </p>
        ) : (
          <div id="match-results-sections" data-testid="match-results-sections">
            {(store.currentViewData || []).map(
              ({ taskId, columns, metadata }) => (
                <div
                  key={`${store.viewMode}-${taskId}`}
                  id={`match-results-section-${store.viewMode}-${taskId}`}
                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
                >
                  <MatchProspectTable
                    sectionId={`${store.viewMode}-${taskId}`}
                    taskId={taskId}
                    algorithm={metadata?.algorithm}
                    numCandidates={metadata?.numCandidates}
                    date={metadata?.date}
                    thisEncounterImageUrl={metadata?.queryImageUrl}
                    thisEncounterAnnotations={[
                      metadata?.queryEncounterAnnotation,
                    ]}
                    thisEncounterImageAsset={metadata?.queryEncounterImageAsset}
                    methodName={metadata?.methodName}
                    methodDescription={metadata?.methodDescription}
                    taskStatus={metadata?.taskStatus}
                    taskStatusOverall={metadata?.taskStatusOverall}
                    emptyStateType={metadata?.emptyStateType}
                    errors={metadata?.errors}
                    themeColor={themeColor}
                    columns={columns}
                    selectedMatch={store.selectedMatch}
                    onToggleSelected={(
                      checked,
                      key,
                      encounterId,
                      individualId,
                      individualDisplayName,
                    ) => {
                      store.setSelectedMatch(
                        checked,
                        key,
                        encounterId,
                        individualId,
                        individualDisplayName,
                      );
                    }}
                  />
                </div>
              ),
            )}
          </div>
        )}
      </div>
    </Container>
  );
});

export default MatchResults;

 succeeded in 289ms:
    return this._selectedMatch;
  }

  get uniqueIndividualIds() {
    const ids = new Set();

    if (this._individualId) {
      ids.add(this._individualId);
    }

    this._selectedMatch.forEach((match) => {
      if (match.individualId) {
        ids.add(match.individualId);
      }
    });

    return Array.from(ids);
  }

  get querySelectionItem() {
    if (!this._encounterId) return null;
    return {
      encounterId: this._encounterId,
      individualId: this._individualId || null,
      individualDisplayName: this.individualDisplayName || null,
    };
  }

  get selectedIncludingQuery() {
    const selected = Array.isArray(this._selectedMatch)
      ? this._selectedMatch
      : [];
    const q = this.querySelectionItem;
    if (!q) return selected;

    const withoutQueryDup = selected.filter(
      (m) => m?.encounterId && m.encounterId !== q.encounterId,
    );

    return [q, ...withoutQueryDup];
  }

  async fetchMatchResults({ silent = false } = {}) {
    if (!this._taskId) return;

    // Capture request context to detect stale responses
    const requestId = Date.now() + Math.random();
    this._currentRequestId = requestId;
    const capturedTaskId = this._taskId;

    const params = new URLSearchParams();
    params.set("prospectsSize", String(this.numResults));

    if (Array.isArray(this._projectNames) && this._projectNames.length > 0) {
      this._projectNames.forEach((projectId) =>
        params.append("projectId", projectId),
      );
    }

    if (!silent) {
      this.setLoading(true);
      this.clearResults();

      try {
        const result = await axios.get(
          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
        );
        // Discard stale responses
        if (
          this._currentRequestId !== requestId ||
          this._taskId !== capturedTaskId
        ) {
          return;
        }
        this.loadData(result?.data, { preserveSelection: false });
      } catch {
        this.clearResults();
        toast.error("Failed to load match results");
      } finally {
        // Only clear loading if this is still the current request
        if (this._currentRequestId === requestId) {
          this.setLoading(false);
        }
      }
    } else {
      try {
        const result = await axios.get(
          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
        );
        // Discard stale responses
        if (
          this._currentRequestId !== requestId ||
          this._taskId !== capturedTaskId
        ) {
          return;
        }

        const root = result?.data?.matchResultsRoot;

        const stillRunning = isMatchTaskStillRunning(root);
        const annLen = (getAllAnnot(root) || []).length;
        const indLen = (getAllIndiv(root) || []).length;
        const hasAnyResults = annLen > 0 || indLen > 0;

        if (stillRunning && !hasAnyResults) {
          return;
        }

        this.loadData(result?.data, { preserveSelection: true });
      } catch (e) {
        throw new Error(
          "Failed to silently refresh match results: " +
            (e?.message || String(e)),
        );
      }
    }
  }

  // setters and actions

  setLoading(loading) {
    this._loading = loading;
  }

  setHasResults(results) {
    this._hasResults = results;
  }

  setTaskId(id) {
    this._taskId = id;
  }

  setViewMode(mode) {
    this._viewMode = mode;
  }

  setNumResults(n) {
    const num = Number(n);
    if (!Number.isFinite(num)) return;
    this._numResults = Math.min(Math.max(Math.floor(num), 1), MAX_NUM_RESULTS);
  }

  setProjectNames(names, { fetch = true } = {}) {
    const next = Array.isArray(names) ? names : [];

    // Compare sorted copies to avoid spurious refetches when order differs
    const currentSorted = [...this._projectNames].sort();
    const nextSorted = [...next].sort();
    if (JSON.stringify(currentSorted) === JSON.stringify(nextSorted)) return;

    this._projectNames = next;

    if (fetch && this._taskId) {
      this.fetchMatchResults();
    }
  }

  setNewIndividualName(name, useNextName = false) {
    this._useNextIndividualName = useNextName;
    this._newIndividualName = name;
  }

  async handleCreateNewIndividual(selectedRemark) {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const newName = (this._newIndividualName || "").trim();

      if (!newName) {
        this._matchRequestError = "ENTER_INDIVIDUAL_NAME";
        toast.error("Please enter a new individual name");
        return { ok: false, error: "ENTER_INDIVIDUAL_NAME" };
      }

      const encounterIds = Array.from(
        new Set(
          this.selectedIncludingQuery
            .filter((m) => !m.individualId)
            .map((m) => m.encounterId),
        ),
      );

      if (encounterIds.length === 0) {
        this._matchRequestError = "NO_ENCOUNTERS_TO_UPDATE";
        toast.error("No encounters to update");
        return { ok: false, error: "NO_ENCOUNTERS_TO_UPDATE" };
      }

      let patchOps = [];

      if (this._useNextIndividualName) {
        patchOps = [
          {
            op: "replace",
            path: "individualId",
            value: {
              type: "locationId",
              value: this._encounterLocationId,
            },
          },
        ];
      } else {
        patchOps = [{ op: "replace", path: "individualId", value: newName }];
      }

      if (selectedRemark && selectedRemark.trim() !== "") {
        patchOps.push({
          op: "replace",
          path: "identificationRemarks",
          value: selectedRemark,
        });
      }

      // Run all PATCHes in parallel and track results
      const patchPromises = encounterIds.map((id) =>
        axios
          .patch(`/api/v3/encounters/${encodeURIComponent(id)}`, patchOps, {
            headers: {
              "Content-Type": "application/json-patch+json",
              Accept: "application/json",
            },
          })
          .then(
            (response) => ({ status: "fulfilled", encounterId: id, response }),
            (error) => ({ status: "rejected", encounterId: id, error }),
          ),
      );

      const results = await Promise.allSettled(patchPromises);

      // Separate successes and failures
      const successes = [];
      const failures = [];

      for (const result of results) {
        if (result.status === "fulfilled") {
          const { status, encounterId, error } = result.value;
          if (status === "fulfilled") {
            successes.push(encounterId);
          } else {
            failures.push({ encounterId, error });
          }
        }
      }

      // If any failed, show detailed error
      if (failures.length > 0) {
        const failedIds = failures.map((f) => f.encounterId).join(", ");
        this._matchRequestError = "CREATE_NEW_INDIVIDUAL_PARTIAL";
        toast.error(
          `Failed to update ${failures.length} of ${encounterIds.length} encounters: ${failedIds}`,
        );
        return {
          ok: false,
          error: "CREATE_NEW_INDIVIDUAL_PARTIAL",
          successes,
          failures: failures.map((f) => ({
            encounterId: f.encounterId,
            error: f.error?.message || String(f.error),
          })),
        };
      }

      this.resetSelectionToQuery();
      toast.success("New individual created successfully!");
      return { ok: true, successes };
    } catch {
      this._matchRequestError = "CREATE_NEW_INDIVIDUAL_FAILED";
      toast.error("Failed to create new individual");
      return { ok: false, error: "CREATE_NEW_INDIVIDUAL_FAILED" };
    } finally {
      this._matchRequestLoading = false;
    }
  }

  setSelectedMatch(
    selected,
    key,
    encounterId,
    individualId,
    individualDisplayName,
  ) {
    if (!key || !encounterId) return;

    if (selected) {
      if (this._selectedMatch.some((m) => m.key === key)) return;
      this._selectedMatch = [
        ...this._selectedMatch,
        {
          key,
          encounterId,
          individualId: individualId || null,
          individualDisplayName: individualDisplayName || null,
        },
      ];
    } else {
      this._selectedMatch = this._selectedMatch.filter((m) => m.key !== key);
    }
  }

  clearSelection() {
    this._selectedMatch = [];
    this._matchRequestError = null;
  }

  // merge functions

  //no further action needed
  handleNoFurtherActionNeeded() {
    this.clearSelection();
    return { ok: true, noop: true };
  }

  //one individual
  async handleMatch() {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const all = this.selectedIncludingQuery;

      const uniqueIndividuals = Array.from(
        new Set(all.map((m) => m?.individualId).filter(Boolean)),
      );

      if (uniqueIndividuals.length !== 1) {
        this._matchRequestError = "MATCH_REQUIRES_SINGLE_INDIVIDUAL";
        toast.error("Please select exactly one target individual");
        return null;
      }

      const targetIndividualId = uniqueIndividuals[0];

      const unnamedEncounterIds = Array.from(
        new Set(
          all
            .filter((m) => m?.encounterId && !m?.individualId)
            .map((m) => m.encounterId)
            .filter(Boolean),
        ),
      );

      const params = new URLSearchParams();
      if (this._encounterId) params.set("number", this._encounterId);
      if (this._taskId) params.set("taskId", this._taskId);
      params.set("individualID", targetIndividualId);

      unnamedEncounterIds
        .filter((id) => id !== this._encounterId)
        .forEach((id) => params.append("encOther", id));

      const url = `/iaResultsSetID.jsp?${params.toString()}`;

      const res = await axios.get(url, {
        headers: { Accept: "application/json" },
      });

      this.resetSelectionToQuery();
      toast.success("Match confirmed successfully!");
      return res.data;
    } catch {
      this._matchRequestError = "MATCH_FAILED";
      toast.error("Failed to confirm match");
      return null;
    } finally {
      this._matchRequestLoading = false;
    }
  }

  //merge two individuals and encounters
  async handleMerge() {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const all = this.selectedIncludingQuery;

      const uniqueIndividuals = Array.from(
        new Set(all.map((m) => m?.individualId).filter(Boolean)),
      );

      if (uniqueIndividuals.length !== 2) {
        this._matchRequestError = "MERGE_REQUIRES_TWO_INDIVIDUALS";
        toast.error("Please select exactly two individuals to merge");
        return null;
      }

      const [individualA, individualB] = uniqueIndividuals;

      const unnamedEncounterIds = Array.from(
        new Set(
          all
            .filter((m) => m?.encounterId && !m?.individualId)
            .map((m) => m.encounterId)
            .filter(Boolean),
        ),
      );

      const params = new URLSearchParams();
      params.set("individualA", individualA);

 succeeded in 308ms:
const collectProspects = (node, type, result = []) => {
  if (!node) return result;

  const hasMethod = !!node.method;
  const taskCreated = !!node.statusOverall || hasMethod || !!node.dateCreated;
  const methodName = node.method?.name ?? node.method?.description;
  const methodDescription = node.method?.description ?? null;

  const prospects = node.matchResults?.prospects?.[type];

  const safeProspects = Array.isArray(prospects)
    ? prospects.filter((p) => p && typeof p === "object")
    : [];

  const numberCandidatesRaw = node.matchResults?.numberCandidates;
  const numberCandidates =
    typeof numberCandidatesRaw === "number" ? numberCandidatesRaw : "-";

  let emptyStateType = null;

  if (numberCandidates === 0) {
    emptyStateType = "no_candidates";
  } else if (numberCandidates > 0 && safeProspects.length === 0) {
    emptyStateType = "no_prospects";
  }

  const taskStatusOverall = node.statusOverall ?? null;
  const nodeIsTerminal = isTerminalStatus(taskStatusOverall);
  const nodeIsStillRunning = !!taskStatusOverall && !nodeIsTerminal;

  if (taskCreated) {
    const common = {
      algorithm: methodName,
      date: node.dateCreated,
      numberCandidates: numberCandidatesRaw ?? "-",
      queryEncounterId:
        node.matchResults?.queryAnnotation?.encounter?.id ?? null,
      encounterLocationId:
        node.matchResults?.queryAnnotation?.encounter?.locationId ?? null,
      matchingSetFilter: node.matchingSetFilter,
      queryIndividualId:
        node.matchResults?.queryAnnotation?.individual?.id ?? null,
      queryIndividualDisplayName:
        node.matchResults?.queryAnnotation?.individual?.displayName ?? null,
      queryEncounterImageAsset:
        node.matchResults?.queryAnnotation?.asset ?? null,
      queryEncounterImageUrl:
        node.matchResults?.queryAnnotation?.asset?.url ?? null,
      queryEncounterAnnotation: node.matchResults?.queryAnnotation
        ? {
            id: node.matchResults.queryAnnotation?.id,
            x: node.matchResults.queryAnnotation?.x,
            y: node.matchResults.queryAnnotation?.y,
            width: node.matchResults.queryAnnotation?.width,
            height: node.matchResults.queryAnnotation?.height,
            theta: node.matchResults.queryAnnotation?.theta,
            boundingBox: node.matchResults.queryAnnotation?.boundingBox,
            isTrivial: node.matchResults.queryAnnotation?.isTrivial,
            trivial: node.matchResults.queryAnnotation?.trivial,
          }
        : null,
      methodName,
      methodDescription,
      method: node.method ?? null,
      taskId: node.id ?? null,
      taskStatus: node.status ?? null,
      taskStatusOverall,
      hasResults: safeProspects.length > 0,
      emptyStateType,
      errors: node.statusDetails?.errors ?? null,
    };

    if (safeProspects.length > 0) {
      result.push(
        ...safeProspects.map((item) => ({
          ...item,
          ...common,
        })),
      );
    } else if (nodeIsStillRunning || nodeIsTerminal) {
      result.push(common);
    }
  }

  if (Array.isArray(node.children)) {
    node.children.forEach((child) => collectProspects(child, type, result));
  }

  return result;
};

export const getAllIndiv = (node, result = []) =>
  collectProspects(node, "indiv", result);
export const getAllAnnot = (node, result = []) =>
  collectProspects(node, "annot", result);

const isLeafNode = (node) => {
  if (!node || typeof node !== "object") return false;
  return !Array.isArray(node.children) || node.children.length === 0;
};

const collectLeafNodes = (node, result = []) => {
  if (!node || typeof node !== "object") return result;

  if (isLeafNode(node)) {
    result.push(node);
    return result;
  }

  if (Array.isArray(node.children)) {
    node.children.forEach((child) => collectLeafNodes(child, result));
  }

  return result;
};

const hasOwnStatusOverall = (node) =>
  !!node &&
  typeof node === "object" &&
  Object.prototype.hasOwnProperty.call(node, "statusOverall");

const isTerminalStatus = (status) =>
  status === "completed" || status === "error";

export const isMatchTaskStillRunning = (root) => {
  if (!root || typeof root !== "object") return false;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  if (nodesToCheck.length === 0) return false;

  return nodesToCheck.some((node) => !isTerminalStatus(node.statusOverall));
};

export const hasMatchTaskError = (root) => {
  if (!root || typeof root !== "object") return false;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  return nodesToCheck.some((node) => node.statusOverall === "error");
};

export const isMatchTaskTerminal = (root) => {
  if (!root || typeof root !== "object") return true;

  const leafNodes = collectLeafNodes(root);
  const nodesToCheck = [root, ...leafNodes].filter(hasOwnStatusOverall);

  if (nodesToCheck.length === 0) return true;

  return nodesToCheck.every((node) => isTerminalStatus(node.statusOverall));
};

 succeeded in 287ms:
import React, { useRef, useState } from "react";
import { Row, Col, Form, Modal, Spinner } from "react-bootstrap";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import HatchMarkIcon from "../icons/HatchMarkIcon";
import ToggleAnnotationIcon from "../icons/ToggleAnnotationIcon";
import FullScreenIcon from "../icons/FullScreenIcon";
import InteractiveAnnotationOverlay from "../../../components/AnnotationOverlay";
import { FormattedMessage, useIntl } from "react-intl";
import InspectorModal from "./InspectorModal";
import ExitFullScreenIcon from "../icons/ExitFullScreenIcon";
import EncounterIcon from "../../../components/icons/EncounterIcon";
import EmptyMatchPlaceholder from "./EmptyMatchPlaceholder";

const styles = {
  matchRow: (selected, themeColor) => ({
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "6px 10px",
    fontSize: "1rem",
    marginTop: "4px",
    borderRadius: "5px",
    backgroundColor: selected
      ? themeColor.primaryColors.primary50
      : "transparent",
  }),
  matchRank: {
    width: "24px",
    textAlign: "right",
    marginRight: "8px",
  },
  idPill: (themeColor) => ({
    borderRadius: "5px",
    border: "none",
    padding: "2px 10px",
    fontSize: "1rem",
    background: themeColor.wildMeColors.teal100,
    color: themeColor.wildMeColors.teal800,
    maxWidth: "200px",
    overflow: "hidden",
  }),
  encounterButton: () => ({
    borderRadius: "50%",
    border: "none",
    fontSize: "1rem",
    display: "flex",
    alignItems: "center",
    gap: "4px",
    width: "20px",
    height: "20px",
    padding: 0,
    lineHeight: 0,
  }),
  matchImageCard: {
    position: "relative",
    borderRadius: "8px",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
    overflow: "hidden",
  },
  imageContainer: {
    width: "100%",
    height: "100%",
    overflow: "hidden",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8f9fa",
  },
  cornerLabel: (themeColor) => ({
    position: "absolute",
    top: "8px",
    left: "-8px",
    background: themeColor.wildMeColors.teal100,
    color: themeColor.wildMeColors.teal800,
    padding: "2px 8px",
    borderRadius: "2px",
    fontSize: "1rem",
    zIndex: 10,
  }),
  toolsBarLeft: {
    position: "absolute",
    top: "0",
    left: "-40px",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  toolsBarRight: {
    position: "absolute",
    top: "0",
    right: "-40px",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  iconButton: {
    width: "32px",
    height: "32px",
    borderRadius: "8px",
    cursor: "pointer",
  },
  iconButtonDisabled: {
    width: "32px",
    height: "32px",
    borderRadius: "8px",
    cursor: "not-allowed",
    opacity: 0.4,
  },
  matchListScrollContainer: {
    overflowX: "auto",
    overflowY: "hidden",
    marginBottom: "1rem",
  },
  matchListGrid: {
    display: "flex",
    gap: "12px",
    width: "100%",
  },
  matchColumn: {
    flex: 1,
    minWidth: "30%",
    display: "flex",
    flexDirection: "column",
  },
  fullscreenBody: {
    padding: 12,
    background: "#111",
    height: "100vh",
  },
  fullscreenGrid: {
    height: "calc(100vh - 24px)",
    display: "flex",
    gap: 12,
  },
  fullscreenPanel: {
    flex: 1,
    minWidth: 0,
    borderRadius: 10,
    overflow: "hidden",
    background: "#1a1a1a",
    position: "relative",
    boxShadow: "0 2px 14px rgba(0,0,0,0.35)",
  },
  fullscreenLabel: {
    position: "absolute",
    top: 10,
    left: 10,
    zIndex: 5,
    background: "rgba(255,255,255,0.92)",
    padding: "3px 10px",
    borderRadius: 6,
    fontSize: 12,
  },
  fullscreenImageWrap: {
    position: "relative",
    width: "100%",
    height: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "#111",
  },
  fullscreenTopRight: {
    position: "absolute",
    top: 10,
    right: 10,
    zIndex: 80,
    display: "flex",
    gap: 8,
  },
};

const MatchProspectTable = ({
  sectionId,
  numCandidates,
  date,
  selectedMatch,
  onToggleSelected,
  thisEncounterImageUrl,

exec
/bin/bash -lc "sed -n '180,520p' frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '520,900p' frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '620,700p' frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js && sed -n '150,230p' frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx" in /mnt/c/Wildbook-clean2
 succeeded in 266ms:
                  gap: "8px",
                }}
                data-testid={`match-prospect-running-${sectionId}`}
              >
                <FormattedMessage
                  id="MATCH_RESULTS_STILL_PROCESSING"
                  defaultMessage="Match results are still being processed."
                />
                <Spinner
                  animation="border"
                  size="sm"
                  role="status"
                  aria-label="Loading match results"
                />
              </div>
            ) : isError ? (
              <div
                className="alert alert-danger mt-3 mb-0"
                role="alert"
                data-testid={`match-prospect-error-${sectionId}`}
              >
                <div className="fw-semibold mb-1">
                  <FormattedMessage
                    id="MATCH_RESULTS_PROCESSING_FAILED"
                    defaultMessage="Match results processing failed."
                  />
                </div>

                {Array.isArray(errors) && errors.length > 0 && (
                  <>
                    <div className="small mb-1">
                      <FormattedMessage
                        id="AN_ERROR_OCCURRED"
                        defaultMessage="An error occurred"
                      />
                    </div>

                    <ul className="mb-0 ps-3">
                      {errors.map((err, index) => (
                        <li key={`${err?.code || "unknown"}-${index}`}>
                          <FormattedMessage
                            id={`MATCH_RESULTS_ERROR_${err?.code}`}
                            defaultMessage="Unknown error"
                          />
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            ) : (
              <div
                className="alert alert-danger mt-3 mb-0"
                role="alert"
                data-testid={`match-prospect-empty-${sectionId}`}
              >
                {emptyStateType === "no_candidates" ||
                emptyStateType === "no_prospects" ? (
                  <FormattedMessage id="NO_MATCH_PROSPECTS" />
                ) : (
                  <FormattedMessage
                    id="NO_MATCH_RESULT"
                    defaultMessage="No match results available."
                  />
                )}
              </div>
            )}
          </div>
        )}
      </div>

      <Row data-testid={`match-prospect-images-${sectionId}`}>
        <Col
          md={6}
          className="mb-3 mb-md-0"
          style={{ position: "relative" }}
          data-testid={`match-prospect-left-col-${sectionId}`}
        >
          <div
            style={styles.matchImageCard}
            data-testid={`match-prospect-left-card-${sectionId}`}
          >
            <div
              style={styles.cornerLabel(themeColor)}
              data-testid={`match-prospect-left-label-${sectionId}`}
            >
              <FormattedMessage id="THIS_ENCOUNTER" />
            </div>
            <div
              style={styles.imageContainer}
              data-testid={`match-prospect-left-overlay-wrap-${sectionId}`}
            >
              {hasLeftImage ? (
                <InteractiveAnnotationOverlay
                  ref={leftOverlayRef}
                  imageUrl={leftImageUrl}
                  originalWidth={leftOrigW}
                  originalHeight={leftOrigH}
                  annotations={leftAnnotations}
                  rotationInfo={leftRotationInfo}
                />
              ) : (
                <EmptyMatchPlaceholder sectionId={`${sectionId}-left`} />
              )}
            </div>
          </div>

          <div
            style={styles.toolsBarLeft}
            data-testid={`match-prospect-left-toolbar-${sectionId}`}
          >
            <div
              onClick={() => leftOverlayRef.current?.zoomIn?.()}
              style={
                hasLeftImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom In"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  leftOverlayRef.current?.zoomIn?.();
                }
              }}
              id={`match-prospect-left-zoom-in-${sectionId}`}
              data-testid={`match-prospect-left-zoom-in-${sectionId}`}
              aria-disabled={!hasLeftImage}
            >
              <ZoomInIcon />
            </div>

            <div
              onClick={() => leftOverlayRef.current?.zoomOut?.()}
              style={
                hasLeftImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom Out"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  leftOverlayRef.current?.zoomOut?.();
                }
              }}
              id={`match-prospect-left-zoom-out-${sectionId}`}
              data-testid={`match-prospect-left-zoom-out-${sectionId}`}
              aria-disabled={!hasLeftImage}
            >
              <ZoomOutIcon />
            </div>
          </div>
        </Col>

        <Col
          md={6}
          style={{ position: "relative" }}
          data-testid={`match-prospect-right-col-${sectionId}`}
        >
          <div
            style={styles.matchImageCard}
            data-testid={`match-prospect-right-card-${sectionId}`}
          >
            <div
              style={{ ...styles.cornerLabel(themeColor) }}
              data-testid={`match-prospect-right-label-${sectionId}`}
            >
              <FormattedMessage id="POSSIBLE_MATCH" />
            </div>
            <div
              style={styles.imageContainer}
              data-testid={`match-prospect-right-overlay-wrap-${sectionId}`}
            >
              {hasRightImage ? (
                <InteractiveAnnotationOverlay
                  ref={rightOverlayRef}
                  imageUrl={rightImageUrl}
                  originalWidth={rightOrigW}
                  originalHeight={rightOrigH}
                  annotations={rightAnnotations}
                  rotationInfo={
                    previewedRow?.annotation?.asset?.rotationInfo ?? null
                  }
                />
              ) : (
                <EmptyMatchPlaceholder sectionId={sectionId} />
              )}
            </div>
          </div>

          <div
            style={styles.toolsBarRight}
            data-testid={`match-prospect-right-toolbar-${sectionId}`}
          >
            <div
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.zoomIn?.();
              }}
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom In"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.zoomIn?.();
                }
              }}
              id={`match-prospect-right-zoom-in-${sectionId}`}
              data-testid={`match-prospect-right-zoom-in-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ZoomInIcon />
            </div>

            <div
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.zoomOut?.();
              }}
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom Out"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.zoomOut?.();
                }
              }}
              id={`match-prospect-right-zoom-out-${sectionId}`}
              data-testid={`match-prospect-right-zoom-out-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ZoomOutIcon />
            </div>

            <div
              style={
                inspectorUrl && hasRightImage
                  ? styles.iconButton
                  : styles.iconButtonDisabled
              }
              title={
                inspectorUrl
                  ? "View inspection visualization"
                  : "No visualization available"
              }
              role="button"
              tabIndex={inspectorUrl && hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!inspectorUrl || !hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  setInspectorOpen(true);
                }
              }}
              onClick={() => {
                if (inspectorUrl && hasRightImage) setInspectorOpen(true);
              }}
              id={`match-prospect-inspector-open-${sectionId}`}
              data-testid={`match-prospect-inspector-open-${sectionId}`}
              aria-disabled={!inspectorUrl || !hasRightImage}
            >
              <HatchMarkIcon />
            </div>

            <div
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="View Annotations"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.toggleAnnotations?.();
                  leftOverlayRef.current?.toggleAnnotations?.();
                }
              }}
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.toggleAnnotations?.();
                leftOverlayRef.current?.toggleAnnotations?.();
              }}
              id={`match-prospect-toggle-annotations-${sectionId}`}
              data-testid={`match-prospect-toggle-annotations-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ToggleAnnotationIcon />
            </div>

            <div
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Fullscreen"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  if (!hasRightImage) return;
                  openFullscreen();
                }
              }}
              onClick={(e) => {
                e.stopPropagation();
                if (!hasRightImage) return;
                openFullscreen();
              }}
              id={`match-prospect-fullscreen-open-${sectionId}`}
              data-testid={`match-prospect-fullscreen-open-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <FullScreenIcon />
            </div>
          </div>
        </Col>
      </Row>

      {hasProspects && hasRightImage && (
        <Modal
          show={fullscreenOpen}
          onHide={() => setFullscreenOpen(false)}
          fullscreen
          centered={false}
          keyboard
          contentClassName="border-0 rounded-0"
          data-testid={`match-prospect-fullscreen-modal-${sectionId}`}
        >
          <div
            style={styles.fullscreenBody}
            data-testid={`match-prospect-fullscreen-body-${sectionId}`}
          >
            <div
              style={styles.fullscreenGrid}
              data-testid={`match-prospect-fullscreen-grid-${sectionId}`}
            >
              <div
                style={styles.fullscreenPanel}
                data-testid={`match-prospect-fullscreen-left-panel-${sectionId}`}
              >
                <div
                  style={styles.fullscreenImageWrap}
                  data-testid={`match-prospect-fullscreen-left-wrap-${sectionId}`}
                >
                  <div
                    style={styles.fullscreenLabel}
                    data-testid={`match-prospect-fullscreen-left-label-${sectionId}`}
                  >
                    <FormattedMessage id="THIS_ENCOUNTER" />
                  </div>

                  <div
                    style={styles.fullscreenTopRight}
                    data-testid={`match-prospect-fullscreen-left-toolbar-${sectionId}`}
                  >
                    <div
                      style={styles.iconButton}
                      title="Zoom In"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsLeftRef.current?.zoomIn?.();
                        }
                      }}
                      onClick={() => fsLeftRef.current?.zoomIn?.()}
                      id={`match-prospect-fullscreen-left-zoom-in-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-left-zoom-in-${sectionId}`}
                    >
                      <ZoomInIcon />
                    </div>
                    <div
                      style={styles.iconButton}
                      title="Zoom Out"
                      role="button"

 succeeded in 275ms:
  thisEncounterImageUrl,
  thisEncounterAnnotations,
  thisEncounterImageAsset,
  themeColor,
  columns = [],
  algorithm,
  methodName,
  methodDescription,
  taskStatusOverall,
  emptyStateType,
  errors,
}) => {
  const intl = useIntl();
  const matchesBasedOnText = intl.formatMessage({ id: "MATCHED_BASED_ON" });
  const leftOverlayRef = useRef(null);
  const rightOverlayRef = useRef(null);

  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const fsLeftRef = useRef(null);
  const fsRightRef = useRef(null);

  const hasProspects = columns.some((columnData) =>
    columnData.some((candidate) => candidate?.annotation),
  );

  const [previewedRow, setPreviewedRow] = useState(() => {
    const first =
      columns
        .flatMap((columnData) => columnData)
        .find((candidate) => candidate?.annotation) ?? null;
    if (!first) return null;
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    return { ...first, _rowKey: firstKey };
  });

  const [inspectorOpen, setInspectorOpen] = useState(false);
  const inspectorUrl = previewedRow?.asset?.url;
  const inspectorOrigW = previewedRow?.asset?.width;
  const inspectorOrigH = previewedRow?.asset?.height;

  React.useEffect(() => {
    const flat = columns.flatMap((columnData) => columnData);
    const candidates = flat.filter((candidate) => candidate?.annotation);

    if (candidates.length === 0) {
      setPreviewedRow(null);
      return;
    }

    setPreviewedRow((prev) => {
      if (prev?.annotation?.id) {
        const matched = candidates.find(
          (candidate) => candidate?.annotation?.id === prev.annotation.id,
        );

        if (matched) {
          const matchedKey = `${matched.annotation?.id}-${matched.displayIndex}`;
          return { ...matched, _rowKey: matchedKey };
        }
      }

      const first = candidates[0];
      const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
      return { ...first, _rowKey: firstKey };
    });
  }, [columns]);

  const [hoveredRow, setHoveredRow] = React.useState(null);

  const handleRowClick = (rowData, rowKey) => {
    setPreviewedRow({ ...rowData, _rowKey: rowKey });
    rightOverlayRef.current?.reset?.();
  };

  const isSelected = (rowKey) => selectedMatch?.some((d) => d.key === rowKey);

  const rightAnnotations = React.useMemo(() => {
    const ann = previewedRow?.annotation;
    if (!ann) return [];
    return [
      {
        id: ann.id,
        boundingBox: ann.boundingBox,
        x: ann.x,
        y: ann.y,
        width: ann.width,
        height: ann.height,
        theta: ann.theta,
        trivial: ann.isTrivial || ann.trivial,
      },
    ];
  }, [previewedRow]);

  const rightImageUrl = previewedRow?.annotation?.asset?.url;

  const leftOrigW =
    thisEncounterImageAsset?.attributes?.width ??
    thisEncounterImageAsset?.width;
  const leftOrigH =
    thisEncounterImageAsset?.attributes?.height ??
    thisEncounterImageAsset?.height;

  const leftAnnotations = thisEncounterAnnotations;
  const leftRotationInfo = thisEncounterImageAsset?.rotationInfo;

  const rightOrigW =
    previewedRow?.annotation?.asset?.width ??
    previewedRow?.annotation?.asset?.attributes?.width;
  const rightOrigH =
    previewedRow?.annotation?.asset?.height ??
    previewedRow?.annotation?.asset?.attributes?.height;

  const leftImageUrl = thisEncounterImageUrl;
  const hasLeftImage = Boolean(leftImageUrl);
  const hasRightImage = Boolean(rightImageUrl);

  const openFullscreen = () => {
    if (!hasRightImage) return;
    setFullscreenOpen(true);
  };

  React.useEffect(() => {
    if (!fullscreenOpen) return;
    fsLeftRef.current?.reset?.();
    fsRightRef.current?.reset?.();
  }, [fullscreenOpen]);

  const isStillRunning =
    !!taskStatusOverall &&
    taskStatusOverall !== "completed" &&
    taskStatusOverall !== "error";

  const isError = taskStatusOverall === "error";

  return (
    <div
      className="mb-4"
      id={sectionId}
      data-testid={`match-prospect-table-${sectionId}`}
    >
      <div
        className="d-flex justify-content-between align-items-center mb-2"
        data-testid={`match-prospect-header-${sectionId}`}
      >
        <div className="d-flex w-100">
          <div
            style={{ fontWeight: "500" }}
            data-testid={`match-prospect-method-${sectionId}`}
          >
            {methodDescription
              ? `${matchesBasedOnText} ${methodDescription}`
              : methodName
                ? `${matchesBasedOnText} ${methodName}`
                : algorithm}
          </div>

          <div
            className="d-flex flex-row gap-3"
            style={{ marginLeft: "auto" }}
            data-testid={`match-prospect-meta-${sectionId}`}
          >
            <div
              style={{
                backgroundColor: themeColor.primaryColors.primary50,
                borderRadius: "4px",
                padding: "4px",
              }}
              data-testid={`match-prospect-candidates-${sectionId}`}
            >
              <FormattedMessage id="AGAINST" /> {numCandidates}{" "}
              <FormattedMessage id="CANDIDATES" />{" "}
            </div>

            <div
              style={{
                backgroundColor: themeColor.primaryColors.primary50,
                borderRadius: "4px",
                padding: "4px",
              }}
              data-testid={`match-prospect-date-${sectionId}`}
            >
              <span>{date?.slice(0, 16)?.replace("T", " ")}</span>
            </div>
          </div>
        </div>
      </div>

      <div
        style={styles.matchListScrollContainer}
        data-testid={`match-prospect-list-scroll-${sectionId}`}
      >
        {hasProspects ? (
          <div
            style={styles.matchListGrid}
            data-testid={`match-prospect-list-${sectionId}`}
          >
            {columns.map((columnData, columnIndex) => (
              <div
                key={columnIndex}
                style={styles.matchColumn}
                data-testid={`match-prospect-column-${sectionId}-${columnIndex}`}
              >
                {columnData
                  .filter((candidate) => candidate?.annotation)
                  .map((candidate) => {
                    const candidateEncounterId =
                      candidate.annotation?.encounter?.id;
                    const candidateIndividualId =
                      candidate.annotation?.individual?.id;
                    const candidateIndividualDisplayName =
                      candidate.annotation?.individual?.displayName;

                    const canOpenEncounter = Boolean(candidateEncounterId);
                    const canOpenIndividual = Boolean(candidateIndividualId);

                    const rowKey = `${candidate.annotation?.id ?? candidate.annotation?.encounter?.id ?? "no-annot"}-${candidate.displayIndex ?? "no-idx"}`;
                    const isRowSelected = isSelected(rowKey);
                    const isRowPreviewed = rowKey === previewedRow?._rowKey;
                    const isRowHovered = rowKey === hoveredRow;

                    return (
                      <div
                        key={rowKey}
                        id={`match-prospect-row-${sectionId}-${rowKey}`}
                        data-testid={`match-prospect-row-${sectionId}-${rowKey}`}
                        onClick={() => handleRowClick(candidate, rowKey)}
                        style={{
                          ...styles.matchRow(isRowSelected, themeColor),
                          cursor: "pointer",
                          backgroundColor:
                            isRowPreviewed || isRowHovered
                              ? themeColor.primaryColors.primary50
                              : "transparent",
                        }}
                        onMouseEnter={() => setHoveredRow(rowKey)}
                        onMouseLeave={() => setHoveredRow(null)}
                      >
                        <span
                          style={styles.matchRank}
                          data-testid={`match-prospect-rank-${sectionId}-${rowKey}`}
                        >
                          {candidate.displayIndex}.
                        </span>

                        <span
                          id={`match-prospect-score-${sectionId}-${rowKey}`}
                          data-testid={`match-prospect-score-${sectionId}-${rowKey}`}
                          style={{
                            maxWidth: "150px",
                            overflow: "hidden",
                          }}
                        >
                          {Number.isFinite(candidate?.score)
                            ? Math.max(candidate.score, 0).toLocaleString(
                                undefined,
                                { maximumFractionDigits: 4 },
                              )
                            : "—"}
                        </span>

                        <button
                          type="button"
                          id={`match-prospect-individual-${sectionId}-${rowKey}`}
                          data-testid={`match-prospect-individual-${sectionId}-${rowKey}`}
                          style={styles.idPill(themeColor)}
                          className="btn btn-sm p-0 px-2"
                          onClick={(e) => {
                            e.stopPropagation();
                            if (!canOpenIndividual) return;
                            const url = `/individuals.jsp?id=${encodeURIComponent(candidateIndividualId)}`;
                            window.open(url, "_blank");
                          }}
                        >
                          {candidateIndividualDisplayName ||
                            candidateIndividualId}
                        </button>

                        {(isRowHovered || isRowSelected) && (
                          <button
                            type="button"
                            title={
                              canOpenEncounter
                                ? `Open Encounter Page (${candidateEncounterId})`
                                : "Open Encounter Page"
                            }
                            id={`match-prospect-encounter-btn-${sectionId}-${rowKey}`}
                            data-testid={`match-prospect-encounter-btn-${sectionId}-${rowKey}`}
                            style={styles.encounterButton(themeColor)}
                            className="btn btn-sm p-0 px-2"
                            onClick={(e) => {
                              e.stopPropagation();
                              if (!canOpenEncounter) return;
                              const url = `/react/encounter?number=${encodeURIComponent(candidateEncounterId)}`;
                              window.open(url, "_blank");
                            }}
                          >
                            <EncounterIcon />
                          </button>
                        )}

                        <div style={{ flexGrow: 1 }} />

                        <div
                          style={{
                            display: "flex",
                            alignItems: "center",
                            gap: "20px",
                          }}
                          onClick={(e) => e.stopPropagation()}
                          data-testid={`match-prospect-actions-${sectionId}-${rowKey}`}
                        >
                          <Form.Check
                            type="checkbox"
                            id={`match-prospect-select-${sectionId}-${rowKey}`}
                            data-testid={`match-prospect-select-${sectionId}-${rowKey}`}
                            checked={isRowSelected}
                            onChange={(e) =>
                              onToggleSelected(
                                e.target.checked,
                                rowKey,
                                candidateEncounterId,
                                candidateIndividualId,
                                candidateIndividualDisplayName,
                              )
                            }
                          />
                        </div>
                      </div>
                    );
                  })}
              </div>
            ))}
          </div>
        ) : (
          <div className="mt-3">
            {isStillRunning ? (
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "8px",

 succeeded in 279ms:
import React from "react";

const EmptyMatchPlaceholder = ({ sectionId }) => (
  <div
    data-testid={`match-prospect-right-placeholder-${sectionId}`}
    style={{
      width: "100%",
      height: "100%",
      minHeight: "320px",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      backgroundColor: "#f8f9fa",
    }}
  >
    <svg
      width="70"
      height="63"
      viewBox="0 0 70 63"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <path
        d="M46.6696 15.5563C38.0781 6.96482 24.1485 6.96482 15.5569 15.5563L0.00058341 31.1127L15.5569 46.669C24.1485 55.2606 38.0781 55.2606 46.6696 46.669C55.2612 38.0775 55.2612 24.1479 46.6696 15.5563Z"
        fill="#798086"
      />
      <g filter="url(#filter0_d_348_12301)">
        <path
          fillRule="evenodd"
          clipRule="evenodd"
          d="M22.5569 15.4436C31.1484 6.85211 45.0786 6.85213 53.6702 15.4436L69.2258 31.0003L53.6702 46.5569C45.0786 55.1484 31.1484 55.1484 22.5569 46.5569C13.9654 37.9654 13.9654 24.0352 22.5569 15.4436Z"
          fill="#798086"
        />
      </g>
      <path
        d="M30.1172 30.8882C30.1172 26.4699 33.6989 22.8882 38.1172 22.8882C42.5355 22.8882 46.1172 26.4699 46.1172 30.8882C46.117 35.3063 42.5353 38.8882 38.1172 38.8882C33.6991 38.8881 30.1174 35.3063 30.1172 30.8882Z"
        fill="#E5E6E7"
      />
      <defs>
        <filter
          id="filter0_d_348_12301"
          x="14.1133"
          y="9"
          width="55.1133"
          height="44.0005"
          filterUnits="userSpaceOnUse"
          colorInterpolationFilters="sRGB"
        >
          <feFlood floodOpacity="0" result="BackgroundImageFix" />
          <feColorMatrix
            in="SourceAlpha"
            type="matrix"
            values="0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 127 0"
            result="hardAlpha"
          />
          <feOffset dx="-2" />
          <feComposite in2="hardAlpha" operator="out" />
          <feColorMatrix
            type="matrix"
            values="0 0 0 0 1 0 0 0 0 1 0 0 0 0 1 0 0 0 1 0"
          />
          <feBlend
            mode="normal"
            in2="BackgroundImageFix"
            result="effect1_dropShadow_348_12301"
          />
          <feBlend
            mode="normal"
            in="SourceGraphic"
            in2="effect1_dropShadow_348_12301"
            result="shape"
          />
        </filter>
      </defs>
    </svg>
  </div>
);

export default EmptyMatchPlaceholder;

 succeeded in 272ms:
    const store = new MatchResultsStore();
    store.setSelectedMatch(true, "k1", "enc-1", "ind-1", "N");
    const result = store.handleNoFurtherActionNeeded();
    expect(result).toEqual({ ok: true, noop: true });
    expect(store.selectedMatch).toEqual([]);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — fetchMatchResults", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    jest.clearAllMocks();
  });

  test("does nothing when taskId is not set", async () => {
    await store.fetchMatchResults();
    expect(axios.get).not.toHaveBeenCalled();
  });

  test("calls correct endpoint with prospectsSize param", async () => {
    store.setTaskId("task-abc");
    store._numResults = 5;
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    await store.fetchMatchResults();
    expect(axios.get).toHaveBeenCalledWith(
      expect.stringContaining("/api/v3/tasks/task-abc/match-results"),
    );
    expect(axios.get).toHaveBeenCalledWith(
      expect.stringContaining("prospectsSize=5"),
    );
  });

  test("appends projectId params when projectNames is set", async () => {
    store.setTaskId("task-xyz");
    store._projectNames = ["proj-a", "proj-b"];
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    await store.fetchMatchResults();
    const url = axios.get.mock.calls[0][0];
    expect(url).toContain("projectId=proj-a");
    expect(url).toContain("projectId=proj-b");
  });

  test("sets loading to false after success", async () => {
    store.setTaskId("t1");
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    await store.fetchMatchResults();
    expect(store.loading).toBe(false);
  });

  test("sets loading to false after error", async () => {
    store.setTaskId("t1");
    axios.get.mockRejectedValueOnce(new Error("network error"));
    await store.fetchMatchResults();
    expect(store.loading).toBe(false);
  });

  test("loads data into store after successful fetch", async () => {
    store.setTaskId("t1");
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    await store.fetchMatchResults();
    expect(store.hasResults).toBe(true);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — handleCreateNewIndividual", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.loadData(makeApiResponse());
    jest.clearAllMocks();
  });

  test("returns error when newIndividualName is empty", async () => {
    store.setNewIndividualName("");
    const result = await store.handleCreateNewIndividual(null);
    expect(result).toEqual({ ok: false, error: "ENTER_INDIVIDUAL_NAME" });
      }),
    );
  }
  MultiSelectWithCheckbox.displayName = "MultiSelectWithCheckbox";
  return MultiSelectWithCheckbox;
});

// ---------------------------------------------------------------------------

const makeApiResponse = () => ({
  matchResultsRoot: {
    id: "task-1",
    status: "complete",
    statusOverall: "complete",
    dateCreated: "2024-06-01",
    method: { name: "hotspotter", description: "HotSpotter" },
    matchingSetFilter: {},
    matchResults: {
      numberCandidates: 10,
      queryAnnotation: {
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
        asset: { url: "http://img.test/query.jpg" },
        encounter: { id: "enc-query", locationId: "loc-1" },
        individual: { id: "ind-query", displayName: "Luna" },
      },
      prospects: {
        annot: [{ annotId: "a1", score: 0.9 }],
        indiv: [{ individualId: "i1", score: 0.85 }],
      },
    },
    children: [],
  },
});

const renderComponent = (url = "/match-results?taskId=task-1") =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MemoryRouter initialEntries={[url]}>
        <MatchResults />
      </MemoryRouter>
    </IntlProvider>,
  );

// ---------------------------------------------------------------------------

describe("MatchResults component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("shows loader while fetching", async () => {
    let resolveRequest;
    axios.get.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = () => resolve({ data: makeApiResponse() });
        }),
    );
    renderComponent();
    expect(screen.getByTestId("full-screen-loader")).toBeInTheDocument();
    await act(async () => {
      resolveRequest();
    });
  });

  test("shows 'no match results' message when no taskId in URL", async () => {
    renderComponent("/match-results");
    expect(await screen.findByText(/NO_MATCH_RESULT/i)).toBeInTheDocument();
  });

  test("renders match prospect table after successful fetch", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    expect(
      await screen.findByTestId("prospect-table-task-1"),
    ).toBeInTheDocument();
  });

codex
The frontend normalization is more specific than the backend serializer note: it expects `matchResults.prospects.indiv` and `.annot` arrays, and each displayed row must contain `candidate.annotation`. If the backend is returning only the raw `{annotation, score, asset}` array without the `annot`/`indiv` wrapper, or if the UI is stuck in the wrong view mode, that would explain a blank list. I’m checking the Java `Task` and `MatchResult` JSON builders now.
exec
/bin/bash -lc 'rg -n "matchResultsJson|jsonForApiGet|prospects" src/main/java/org/ecocean/Task.java src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/java/org/ecocean/media/MediaAsset.java src/main/java/org/ecocean/Annotation.java src/main/java/org/ecocean/Encounter.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,220p' src/main/java/org/ecocean/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' src/main/java/org/ecocean/ia/MatchResultProspect.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,260p' src/main/java/org/ecocean/ia/MatchResult.java" in /mnt/c/Wildbook-clean2
 succeeded in 262ms:
package org.ecocean.ia;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
    private Annotation annotation;
    private double score = 0.0d;
    private String scoreType;
    private MediaAsset asset;
    private MatchResult matchResult;

    public MatchResultProspect() {}

    public MatchResultProspect(Annotation ann) {
        this();
        this.annotation = ann;
    }

    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
        this();
        this.annotation = ann;
        this.score = score;
        this.scoreType = type;
        this.asset = asset;
    }

    /**
     * Attach a PairX inspection MediaAsset to this prospect. Used by
     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
     * prospects after the MatchResult has been persisted (empty-match-
     * prospects design Track 2 C13: PairX is now non-blocking and
     * runs without holding the outer Shepherd across HTTP).
     */
    public void setAsset(MediaAsset asset) {
        this.asset = asset;
    }

    public MediaAsset getAsset() {
        return asset;
    }

    public Annotation getAnnotation() {
        return annotation;
    }

    public double getScore() {
        return score;
    }

    public String getType() {
        return scoreType;
    }

    public boolean isType(String type) {
        if (type == null) return (this.scoreType == null);
        return type.equals(this.scoreType);
    }

    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
        // if we have no projects to filter on, we consider this to be in it
        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
        if (annotation == null) return false;
        Encounter enc = annotation.findEncounter(myShepherd);
        if (enc == null) return false;
        return enc.isInProjects(projectIds, myShepherd);
    }

    public String toString() {
        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
    }

    public JSONObject jsonForApiGet(Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
        rtn.put("score", score);
        // skipping scoreType since this is currently only used filtered by scoreType already
        if (asset != null) {
            JSONObject aj = asset.toSimpleJSONObject();
            aj.put("url", asset.webURL()); // we have no "safe" url
            rtn.put("asset", aj);
        }
        return rtn;
    }

    // used in sorting
    @Override public int compareTo(MatchResultProspect other) {
        // we invert this so higher score is first
        int comp = Double.compare(other.score, this.score);
        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
        // ordering (otherwise tied scores come back random order), so we use annot id
        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
            return this.annotation.getId().compareTo(other.annotation.getId());
        // scores are *not* equal, so we just let comparison stand as-is
        return comp;
    }
}

 succeeded in 265ms:
package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.api.UploadedFiles;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.MLService;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.media.AssetStore;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.MarkedIndividual;
import org.ecocean.RestClient;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class MatchResult implements java.io.Serializable {
    private String id;
    private long created;
    private Task task;
    private Set<MatchResultProspect> prospects;
    private Annotation queryAnnotation;
    private int numberCandidates = 0;
    // we store *actual* count here, but they may not all exist
    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
    private int numberProspects = 0;
    // not sure we really *need* true fk link to these annots
    // they might be gone now and will we ever use this?
    // so for now we just populate numberCandidates
    private Set<Annotation> candidates;
    // fallback number to cutoff number of prospects to return
    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;
    // number of MatchResultProspects [per type] to actually store (hotspotter
    // results can produce thousands, but storing them all is excessive)
    public static final int MAXIMUM_PROSPECTS_STORED = 500;

    public MatchResult() {
        id = Util.generateUUID();
        created = System.currentTimeMillis();
    }

    public MatchResult(Task task) {
        this();
        this.task = task;
    }

    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        this();
        this.createFromIdentityServiceLog(isLog, myShepherd);
    }

    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.createFromJsonResult(jsonResult, myShepherd);
    }

    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
        Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.numberCandidates = numberCandidates;
        this.setQueryAnnotationFromTask();
        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
        this.populateProspects(annots, false, myShepherd);
        this.populateProspects(annots, true, myShepherd);
    }

    public int getNumberCandidates() {
        return numberCandidates;
    }

    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        if (isLog == null) throw new IOException("log passed is null");
        String taskId = isLog.getTaskID();
        this.task = myShepherd.getTask(taskId);
        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
        JSONObject res = isLog.getJsonResult();
        if (res == null) {
            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
                isLog.getStatusJson());
            throw new IOException("could not get json result");
        }
        createFromJsonResult(res, myShepherd);
    }

    public Annotation setQueryAnnotationFromTask()
    throws IOException {
        if (this.task == null)
            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
        int numAnns = this.task.countObjectAnnotations();
        if (numAnns < 1)
            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
        if (numAnns > 1)
            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
                " annotations; using first");
        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
        return this.queryAnnotation;
    }

    // json_result section should be passed here
    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
    throws IOException {
        if (res == null) throw new IOException("null json_result passed");
        if (res.optJSONArray("query_annot_uuid_list") == null)
            throw new IOException("no query annot list");
        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
            throw new IOException("empty query annot list");
        // for now we are assuming a single query annot. sorrynotsorry.
        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
            "query_annot_uuid_list").optJSONObject(0));
        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
        if (this.queryAnnotation == null)
            throw new IOException("failed to load query annot from id=" + queryAnnotId);
        if (res.optJSONObject("cm_dict") == null)
            throw new IOException("no cm_dict found in " + res);
        // results is the real scores (etc) we are looking for.... finally!
        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
        if (results == null) throw new IOException("no actual results found");
        // see note at top about true annot list of candidates vs number
        if (res.optJSONArray("database_annot_uuid_list") != null)
            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
/*
        annot_score_list <=> dannot_uuid_list
        score_list is for indiv scores but on dannot_uuid_list (same length)
        name_score_list <=> unique_name_uuid_list ???
 */
        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        System.out.println("[DEBUG] createFromJsonResult() created " + this);
    }

    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
        JSONArray externs, String externRef, Shepherd myShepherd)
    throws IOException {
        if ((annotIds == null) || (scores == null))
            throw new IOException("null annotIds or scores");
        if (annotIds.length() != scores.length())
            throw new IOException("mismatch in size of annotIds/scores");
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        int num = 0;
        this.numberProspects += annotIds.length(); // true number of prospects
        for (int i = 0; i < annotIds.length(); i++) {
            double score = scores.optDouble(i, -Double.MAX_VALUE);
            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
            if (ann == null) {
                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
                    "; skipping; score=" + score);
                continue;
            }
            MediaAsset ma = null;
            // we only try if we have a true value in externs[i]
            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
            num++;
            if (num >= MAXIMUM_PROSPECTS_STORED) {
                System.out.println("[DEBUG] hit max (" + MAXIMUM_PROSPECTS_STORED +
                    ") number storable prospects on " + this);
                break;
            }
        }
        return num;
    }

    // we just have a list of annots which matched (e.g. via vectors in opensearch)
    // NOTE: currently does not check MAXIMUM_PROSPECTS_STORED because vector search
    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
    //
    // Empty-match-prospects design Track 2 C13: prospects are created with
    // {@code asset=null}. The PairX inspection image is populated later by
    // {@link MatchInspectionPairxEnricher} in a Phase A/B/C flow so the
    // outer Shepherd is never held across the PairX HTTP call. Holding a
    // Shepherd across that ~10-30s POST would risk connection-pool
    // exhaustion under load (Codex C12 review High).
    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
        Shepherd myShepherd)
    throws IOException {
        if (Util.collectionIsEmptyOrNull(annots)) return 0;
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        if (scoreByIndividual) {
            // the scores for these are calculated weighted by indiv count
            _populateProspectsByIndividual(annots, myShepherd);
        } else {
            // these scores are direct from opensearch
            for (Annotation ann : annots) {
                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
                    null));
            }
        }
        this.numberProspects = this.prospects.size();
        return this.numberProspects;
    }

    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
            List<Annotation> >();

        for (Annotation ann : annots) {
            Encounter enc = ann.findEncounter(myShepherd);
            // i think we just ignore if no enc/indiv
            if (enc == null) continue;
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv == null) continue;
            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
            tally.get(indiv).add(ann);
        }
        if (tally.size() < 1) return; // no individuals i guess?

        // this sorts by most annots (per indiv) highest to lowest
        List<Map.Entry<MarkedIndividual,
            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
            Map.Entry<MarkedIndividual, List<Annotation> > two) {
                // we reverse order here so we get largest first
                return Integer.compare(two.getValue().size(), one.getValue().size());
            }
        });
        int most = sorted.get(0).getValue().size(); // top num of annots
        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
            double score = new Double(ent.getValue().size()) / new Double(most);
            // the ent value (annot List) should always have at least one annot, so we use first one.
            // Inspection MediaAsset attached later by MatchInspectionPairxEnricher
            // (empty-match-prospects design Track 2 C13).
            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
                null));
        }
    }

    /**

 exited 2 in 289ms:
sed: can't read src/main/java/org/ecocean/Task.java: No such file or directory

 exited 2 in 336ms:
rg: src/main/java/org/ecocean/Task.java: No such file or directory (os error 2)
src/main/java/org/ecocean/ia/MatchResultProspect.java:40:     * prospects after the MatchResult has been persisted (empty-match-
src/main/java/org/ecocean/ia/MatchResultProspect.java:41:     * prospects design Track 2 C13: PairX is now non-blocking and
src/main/java/org/ecocean/ia/MatchResultProspect.java:82:    public JSONObject jsonForApiGet(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
src/main/java/org/ecocean/ia/MatchResult.java:42:    // via .prospects due to MAXIMUM_PROSPECTS_STORED (see below)
src/main/java/org/ecocean/ia/MatchResult.java:48:    // fallback number to cutoff number of prospects to return
src/main/java/org/ecocean/ia/MatchResult.java:84:        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
src/main/java/org/ecocean/ia/MatchResult.java:165:        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:168:        this.numberProspects += annotIds.length(); // true number of prospects
src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:186:                    ") number storable prospects on " + this);
src/main/java/org/ecocean/ia/MatchResult.java:195:    // tends to return relatively few prospects. TODO adjust later if this proves untrue.
src/main/java/org/ecocean/ia/MatchResult.java:197:    // Empty-match-prospects design Track 2 C13: prospects are created with
src/main/java/org/ecocean/ia/MatchResult.java:207:        if (this.prospects == null)
src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:219:        this.numberProspects = this.prospects.size();
src/main/java/org/ecocean/ia/MatchResult.java:254:            // (empty-match-prospects design Track 2 C13).
src/main/java/org/ecocean/ia/MatchResult.java:255:            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
src/main/java/org/ecocean/ia/MatchResult.java:261:     * Public read-only view of the prospects collection so the
src/main/java/org/ecocean/ia/MatchResult.java:267:        return this.prospects;
src/main/java/org/ecocean/ia/MatchResult.java:367:        // empty-match-prospects design Track 2 C12). If either clamped
src/main/java/org/ecocean/ia/MatchResult.java:431:     * <p>Package-visible for unit testing. (Empty-match-prospects
src/main/java/org/ecocean/ia/MatchResult.java:476:     * together (empty-match-prospects design Track 2 C12):</p>
src/main/java/org/ecocean/ia/MatchResult.java:539:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:546:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
src/main/java/org/ecocean/ia/MatchResult.java:551:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:559:    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:564:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
src/main/java/org/ecocean/ia/MatchResult.java:565:                jarr.put(mrp.jsonForApiGet(myShepherd));
src/main/java/org/ecocean/ia/MatchResult.java:572:    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
src/main/java/org/ecocean/ia/MatchResult.java:580:        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
src/main/java/org/ecocean/Encounter.java:1645:    // this is for jsonForApiGet()
src/main/java/org/ecocean/Encounter.java:4773:    public org.json.JSONObject jsonForApiGet(Shepherd myShepherd, User user)

exec
/bin/bash -lc 'rg -n "matchResultsJson|jsonForApiGet|children|statusOverall|dateCreated|matchingSetFilter|objectAnnotations" src/main/java/org/ecocean/ia/Task.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '520,610p' src/main/java/org/ecocean/ia/MatchResult.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '260,560p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,260p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
 succeeded in 259ms:
/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
 */
package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.Query;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.identity.IdentityServiceLog;

public class Task implements java.io.Serializable {
    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    private String id = null;
    private long created = -1;
    private long modified = -1;
    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    private List<MediaAsset> objectMediaAssets = null;
    private List<Annotation> objectAnnotations = null;
    private Task parent = null;
    private List<Task> children = null;
    private String parameters = null;
    private String status;
    // general use, but notably will contain error details when status=error
    private String statusDetails = null;
    private Long completionDateInMilliseconds;
    private String queueResumeMessage;

    public Task() {
        this(Util.generateUUID());
    }
    public Task(String id) {
        this.id = id;
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }
    // makes a child of the passed Task (and inherits the parameters!!)
    public Task(Task p) {
        this();
        this.setParameters(p.getParameters());
        this.setParent(p);
    }

    public String getId() {
        return id;
    }

    public long getCreatedLong() {
        return created;
    }

    public long getModifiedLong() {
        return modified;
    }

    public long timeInactive() {
        long now = System.currentTimeMillis();

        if (modified > 0) return (now - modified);
        if (created > 0) return (now - created);
        // weird or inconclusive:
        return -1l;
    }

    public boolean timedOutDueToInactivity() {
        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    }

    public boolean statusInEndState() {
        if ("completed".equals(status)) return true;
        if ("error".equals(status)) return true;
        // ml-service migration v2: "dropped-stale" is terminal — the task's
        // target was deleted before the queued job ran. Neither success nor
        // error; the inactivity-timeout watchdog must not flip it to error.
        if ("dropped-stale".equals(status)) return true;
        return false;
    }

    public void setModified() {
        modified = System.currentTimeMillis();
    }

    public boolean canUserAccess(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (user.isAdmin(myShepherd)) return true;
        Encounter enc = null;
        // if we have annotations, use first to determine encounter
        if (this.countObjectAnnotations() > 0) {
            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
            MediaAsset ma = this.getObjectMediaAssets().get(0);
            // we iterate over all annots on this asset til we find an encounter.
            // it might be better to find *all* encounters and return access based on each;
            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
            // so i think this means asset-based access of tasks will be rare or unused anyway
            for (Annotation ann : ma.getAnnotations()) {
                if (ann != null) enc = ann.findEncounter(myShepherd);
                if (enc != null) break;
            }
        }
        if (enc == null) return false;
        if (enc.isPubliclyReadable()) return true;
        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
        return enc.canUserAccess(user, myShepherd.getContext());
    }

/*
    // not really convinced these are accurate enough to use
    //   actual computation of these things is complicated
    //   leaving these for future potential exploration, if needed.

    public boolean isTypeDetection() {
        if (this.hasObjectMediaAssets()) return true;
        if (this.hasObjectAnnotations()) return false;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
        return false;
    }
    public boolean isTypeIdentification() {
        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
        return false;
    }

    public boolean initiatedWithDetection() {
        if (this.parameters == null) return false;
        return this.getParameters().optBoolean("ibeis.detection", false);
    }
    public boolean initiatedWithIdentification() {
        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
    }
 */
    public int countObjectMediaAssets() {
        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
    }

    public int countObjectAnnotations() {
        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
    }

    public int countObjects() {
        return countObjectMediaAssets() + countObjectAnnotations();
    }

    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
    public boolean hasObjectMediaAssets() {
        return (countObjectMediaAssets() > 0);
    }

    public boolean hasObjectAnnotations() {
        return (countObjectAnnotations() > 0);
    }

    public boolean hasObjects() {
        return (countObjects() > 0);
    }

    public void setObjectMediaAssets(List<MediaAsset> mas) {
        objectMediaAssets = mas;
    }

    public void setObjectAnnotations(List<Annotation> anns) {
        objectAnnotations = anns;
    }

    public List<MediaAsset> getObjectMediaAssets() {
        return objectMediaAssets;
    }

    public List<Annotation> getObjectAnnotations() {
        return objectAnnotations;
    }

    public int numberMediaAssets() {
        return Util.collectionSize(objectMediaAssets);
    }

    public int numberAnnotations() {
        return Util.collectionSize(objectAnnotations);
    }

    // kinda for convenience?
    public boolean addObject(MediaAsset ma) {
        if (ma == null) return false;
        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
        if (!objectMediaAssets.contains(ma)) {
            objectMediaAssets.add(ma);
            return true;
        }
        return false;
    }

    public boolean addObject(Annotation ann) {
        if (ann == null) return false;
        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
        if (!objectAnnotations.contains(ann)) {
            objectAnnotations.add(ann);
            return true;
        }
        return false;
    }

    public boolean removeObject(Annotation ann) {
        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
            objectAnnotations.remove(ann);
            return true;
        }
        return false;
    }

    public boolean removeObject(MediaAsset ma) {
        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
            objectMediaAssets.remove(ma);
            return true;
        }
        return false;
    }

    public boolean contains(Annotation ann) {
        if (objectAnnotations == null) return false;
        return objectAnnotations.contains(ann);
    }

    public boolean contains(MediaAsset ma) {
        if (objectMediaAssets == null) return false;
        return objectMediaAssets.contains(ma);
    }

    public List<Task> getChildren() {
        return children;
    }

    public void setChildren(List<Task> kids) {
        if (kids == null) {
            children = null;
            return;
        }
        children = new ArrayList<Task>();
        for (Task kid : kids) {
            this.addChild(kid); // let this do the work
        }
    }

 succeeded in 320ms:
35:    private List<Annotation> objectAnnotations = null;
37:    private List<Task> children = null;
156:        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
181:        objectAnnotations = anns;
189:        return objectAnnotations;
197:        return Util.collectionSize(objectAnnotations);
213:        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
214:        if (!objectAnnotations.contains(ann)) {
215:            objectAnnotations.add(ann);
222:        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
223:            objectAnnotations.remove(ann);
238:        if (objectAnnotations == null) return false;
239:        return objectAnnotations.contains(ann);
248:        return children;
253:            children = null;
256:        children = new ArrayList<Task>();
263:        if (children == null) children = new ArrayList<Task>();
264:        if (kid == null) return children;
265:        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
266:        if (!children.contains(kid)) children.add(kid);
267:        return children;
285:        return (children == null) ? 0 : children.size();
300:        for (Task kid : children) {
314:        for (Task kid : children) {
324:        for (Task kid : children) {
336:            for (Task kid : this.children) {
347:            for (Task kid : this.children) {
473:        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
475:            for (Annotation ann : this.objectAnnotations) {
482:            for (Task kid : this.children) {
485:            j.put("children", jc);
533:            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
588:            List<Task> children = getChildren();
590:            for (Task t : children) {
663:            // accumulate status across children
741:        return getParameters().optJSONObject("matchingSetFilter");
820:    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
825:        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
842:            rtn.put("matchingSetFilter", getMatchingSetFilter());
844:            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
846: * note: we try getting it regardless of children ("just in case"); but only try to generate if none
853:                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
864:                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
866:        // now we recurse thru children if applicable
869:            for (Task child : children) {
871:                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
877:            rtn.put("children", charr);
878:            // if we dont have children (leaf nodes) we get the status
882:            rtn.put("statusOverall", getOverallStatus(myShepherd));

 succeeded in 307ms:
        JSONObject params = task.getParameters();
        if (params == null) return null;
        return params.optJSONObject("matchingSetFilter");
    }

/*
    see note at top about candidates vs numberCandidates
    public int numberCandidates() {
        return Util.collectionSize(candidates);
    }
 */
    public int numberProspects() {
        return this.numberProspects;
    }

    public Set<String> prospectScoreTypes() {
        Set<String> types = new HashSet<String>();

        if (numberProspects() == 0) return types;
        for (MatchResultProspect mrp : prospects) {
            types.add(mrp.getType());
        }
        return types;
    }

    // if cutoff < 0 then it will not be truncated at all
    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
        Set<String> projectIds, Shepherd myShepherd) {
        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();

        if (numberProspects() == 0) return pros;
        for (MatchResultProspect mrp : prospects) {
            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
        }
        Collections.sort(pros);
        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
        return pros;
    }

    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject sj = new JSONObject();

        for (String type : prospectScoreTypes()) {
            JSONArray jarr = new JSONArray();
            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
                jarr.put(mrp.jsonForApiGet(myShepherd));
            }
            sj.put(type, jarr);
        }
        return sj;
    }

    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", id);
        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
        rtn.put("numberTotalProspects", numberProspects());
        rtn.put("numberCandidates", getNumberCandidates());
        rtn.put("created", Util.millisToISO8601String(created));
        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
        rtn.put("projectIds", projectIds);
        return rtn;
    }

    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
        JSONObject aj = new JSONObject();

        if (ann == null) return aj;
        MediaAsset ma = ann.getMediaAsset();
        // populate bounding box stuff (note: it may reset aj so must be done first)
        if (ann.getFeatures() != null) {
            for (Feature ft : ann.getFeatures()) {
                if (ft.isUnity()) {
                    aj.put("trivial", true);
                    aj.put("x", 0);
                    aj.put("y", 0);
                    // would be weird to be null, but.....
                    if (ma != null) {
                        aj.put("width", (int)ma.getWidth());
                        aj.put("height", (int)ma.getHeight());
                    }
                } else {
                    // basically if we have more than one feature, only one wins
                    if (ft.getParameters() != null) aj = ft.getParameters();
                }
            }
        }
        if (ma != null) {
            JSONObject mj = ma.toSimpleJSONObject();
            mj.put("rotationInfo", ma.getRotationInfo());

 succeeded in 289ms:
    }

    public List<Task> addChild(Task kid) {
        if (children == null) children = new ArrayList<Task>();
        if (kid == null) return children;
        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
        if (!children.contains(kid)) children.add(kid);
        return children;
    }

    public void setParent(Task t) {
        parent = t;
        t.addChild(this);
    }

    public Task getParent() {
        return parent;
    }

    public String getParentId() {
        if (parent == null) return null;
        return parent.getId();
    }

    public int numChildren() {
        return (children == null) ? 0 : children.size();
    }

    public boolean hasChildren() {
        return (this.numChildren() > 0);
    }

    // omg i am going to assume no looping
    public List<Task> getLeafTasks() {
        List<Task> leaves = new ArrayList<Task>();

        if (!this.hasChildren()) {
            leaves.add(this);
            return leaves;
        }
        for (Task kid : children) {
            leaves.addAll(kid.getLeafTasks());
        }
        return leaves;
    }

    public Task getRootTask() {
        if (parent == null) return this;
        return parent.getRootTask();
    }

    public Task deepContains(Annotation ann) {
        if (this.contains(ann)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ann);
            if (found != null) return found;
        }
        return null;
    }

    public Task deepContains(MediaAsset ma) {
        if (this.contains(ma)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ma);
            if (found != null) return found;
        }
        return null;
    }

    public List<Task> findNodesWithMediaAssets() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectMediaAssets()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithMediaAssets());
            }
        return found;
    }

    public List<Task> findNodesWithAnnotations() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectAnnotations()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithAnnotations());
            }
        return found;
    }

    public Map<String, Integer> detectionStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectMediaAssets()) return cts;
        for (MediaAsset ma : this.getObjectMediaAssets()) {
            String status = ma.getDetectionStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public Map<String, Integer> identificationStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectAnnotations()) return cts;
        for (Annotation ann : this.getObjectAnnotations()) {
            String status = ann.getIdentificationStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public JSONObject getStatusDetails() {
        return Util.stringToJSONObject(statusDetails);
    }

    public void setStatusDetails(String s) {
        statusDetails = s;
    }

    public void setStatusDetails(JSONObject j) {
        if (j == null) {
            statusDetails = null;
        } else {
            statusDetails = j.toString();
        }
    }

    public void setStatusDetailsAddError(String code, String message) {
        JSONObject add = new JSONObject();

        add.put("code", code);
        add.put("message", message);
        setStatusDetailsAddToSection("errors", add);
    }

    public void setStatusDetailsAddLog(String message) {
        JSONObject add = new JSONObject();

        add.put("message", message);
        setStatusDetailsAddToSection("log", add);
    }

    // internal utility method for above
    private void setStatusDetailsAddToSection(String section, JSONObject add) {
        if (add == null) return;
        add.put("timestamp", System.currentTimeMillis());
        JSONObject sd = getStatusDetails();
        if (sd == null) sd = new JSONObject();
        if (sd.optJSONArray(section) == null) sd.put(section, new JSONArray());
        sd.getJSONArray(section).put(add);
        setStatusDetails(sd);
    }

    public JSONObject getParameters() { // only return as JSONObject!
        return Util.stringToJSONObject(parameters);
    }

    // see comment above: should this even be public?  (or exist)
    public void setParameters(String s) { // best be json, yo
        parameters = s;
    }

    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;
        } else {
            parameters = j.toString();
        }
    }

    // convenience method to construct the JSONObject from key/value
    public void setParameters(String key, Object value) {
        if (key == null) return; // nope
        JSONObject j = new JSONObject();
        j.put(key, value); // value object type better be kosher for JSONObject.  :/
        parameters = j.toString();
    }

    // like above, but doesnt (re)set .parameters, will only append/alter the key'ed one
    public void addParameter(String key, Object value) {
        if (key == null) return;
        JSONObject j = this.getParameters();
        if (j == null) j = new JSONObject();
        j.put(key, value);
        parameters = j.toString();
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }

    public JSONObject toJSONObject(boolean includeChildren) {
        JSONObject j = new JSONObject();

        j.put("id", id);
        j.put("parameters", this.getParameters());
        j.put("created", created);
        j.put("modified", modified);
        j.put("createdDate", new DateTime(created));
        j.put("modifiedDate", new DateTime(modified));
        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (MediaAsset ma : this.objectMediaAssets) {
                jo.put(ma.getId());
            }
            j.put("mediaAssetIds", jo);
        }
        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (Annotation ann : this.objectAnnotations) {
                jo.put(ann.getId());
            }
            j.put("annotationIds", jo);
        }
        if (includeChildren && this.hasChildren()) {
            JSONArray jc = new JSONArray();
            for (Task kid : this.children) {
                jc.put(kid.toJSONObject(true)); // we once again assume no looping!  bon chance.
            }
            j.put("children", jc);
        }
        return j;
    }

    // need these two so we can use things like List.contains() on tasks
    public boolean equals(final Object t2) {
        if (t2 == null) return false;
        if (!(t2 instanceof Task)) return false;
        Task two = (Task)t2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }

    public int hashCode() {
        if (id == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append(id)
                   .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
                   .append(numChildren() + "Kids")
                   .append(countObjectMediaAssets() + "MA")
                   .append(countObjectAnnotations() + "Ann")
                   .append("params=" + ((this.getParameters() ==
                null) ? "(none)" : this.getParameters().toString()))
                   .toString();
    }

    public static Task load(String taskId, Shepherd myShepherd) {
        Task t = null;

        try {
            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(
                Task.class, taskId), true)));
        } catch (Exception ex) {}; // swallow jdo not found noise
        return t;
    }

    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
        return getTasksFor(ann, myShepherd, null);
    }

    // TODO: evaluate if we should support versions for multiple objects (when needed)
    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
            + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        if (ordering == null) {
            query.setOrdering("created");
        } else {
            query.setOrdering(ordering);
        }
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ann, myShepherd));
    }

    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
            ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        query.setOrdering("created");

exec
/bin/bash -lc 'rg -n "class GenericObject|@Path|doGet|api/v3|GenericObject" src/main/java/org/ecocean/api/GenericObject.java src/main/webapp/WEB-INF/web.xml src/main/java/org/ecocean/servlet src/main/java/org/ecocean/api' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '610,700p' src/main/java/org/ecocean/ia/MatchResult.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '560,920p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,100p' src/main/java/org/ecocean/api/GenericObject.java && sed -n '1,120p' src/main/webapp/WEB-INF/web.xml" in /mnt/c/Wildbook-clean2
 succeeded in 262ms:
            mj.put("rotationInfo", ma.getRotationInfo());
            aj.put("asset", mj);
        }
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc != null) {
            JSONObject ej = new JSONObject();
            // TODO add "access" permission value if needed?
            ej.put("id", enc.getId());
            ej.put("taxonomy", enc.getTaxonomyString());
            ej.put("locationId", enc.getLocationID());
            aj.put("encounter", ej);
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv != null) {
                JSONObject ij = new JSONObject();
                ij.put("id", indiv.getId());
                ij.put("taxonomy", indiv.getTaxonomyString());
                ij.put("displayName", indiv.getDisplayName());
                ij.put("nickname", indiv.getNickName());
                ij.put("sex", indiv.getSex());
                ij.put("numberEncounters", indiv.getNumEncounters());
                aj.put("individual", ij);
            }
        }
        aj.put("id", ann.getId());
        // ml-service migration v2 §commit #11: surface WBIA registration
        // state so the frontend can disable the "Match with HotSpotter"
        // button until WBIA has acknowledged the annotation. tri-state:
        // null = legacy or not-yet-pending; false = pending registration;
        // true = WBIA acknowledged. Frontend treats anything non-true as
        // "HotSpotter not available yet" with a tooltip.
        Boolean wbiaReg = ann.getWbiaRegistered();
        if (wbiaReg != null) aj.put("wbiaRegistered", wbiaReg.booleanValue());
        return aj;
    }

    public String toString() {
        String s = "MatchResult " + id;

        s += " [" + Util.millisToISO8601String(created) + "]";
        s += " query " + queryAnnotation;
        s += "; numCandidates=" + this.getNumberCandidates();
        s += "; numProspects=" + this.numberProspects();
        s += "; task=" + (task == null ? "null" : task.getId());
        return s;
    }
}

 succeeded in 259ms:
        query.setOrdering("created");
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ma, myShepherd));
    }

    // takes a bunch of tasks and returns only roots (without duplication)
    public static List<Task> onlyRoots(List<Task> all) {
        List<Task> roots = new ArrayList<Task>();

        for (Task t : all) {
            Task r = t.getRootTask();
            if (!roots.contains(r)) roots.add(r);
        }
        return roots;
    }

    public boolean areSelfAndOrAllChildrenComplete() {
        boolean complete = false;

        if (!hasChildren() && completionDateInMilliseconds != null) {
            complete = true;
        } else if (hasChildren()) {
            List<Task> children = getChildren();
            complete = true;
            for (Task t : children) {
                if (!t.areSelfAndOrAllChildrenComplete()) complete = false;
            }
        }
        return complete;
    }

    /**
     * Read the raw persisted status field without the timed-out-task
     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
     * Callers gating read-only decisions on status (e.g., the
     * empty-match-prospects Track 2 batch gate) need this so a read of
     * a sibling task's status doesn't mutate that task as a side
     * effect.
     *
     * <p>(Empty-match-prospects design Track 2 C7.)</p>
     */
    public String getStoredStatus() {
        return this.status;
    }

    public String getStatus(Shepherd myShepherd) {
        // see if we might be dead in the water
        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
            this.status = "error";
            long ti = timeInactive();
            setStatusDetailsAddError("TIMEOUT",
                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
            return this.status;
        }
        // if status is not null, just send it
        if (status != null) return status;
        // otherwise
        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
        String status = "waiting to queue";
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);
        if (logs != null && logs.size() > 0) {
            Collections.reverse(logs); // so it has newest first like mostRecent above
            IdentityServiceLog l = logs.get(0);
            JSONObject islObj = l.toJSONObject();
            if (islObj.optString("status") != null &&
                islObj.optString("status").equals("completed")) {
                status = islObj.optString("status");
            } else if (islObj.optJSONObject("status") != null &&
                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
                status = "completed";
            } else if (logs.toString().indexOf("score") > -1) {
                status = "completed";
            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
                status = "error";
            } else if (!islObj.optString("queueStatus").equals("")) {
                status = islObj.optString("queueStatus");
            } else if (islObj.opt("status") != null &&
                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
                status = "queuing";
            }
            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
        }
        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
            this.getId());
        return status;
    }

    // this is stitched together from import.jsp. godspeed.
    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
    // also the original building/modification of (passed-in) idStatusMap is dropped
    public String getOverallStatus(Shepherd myShepherd) {
        String status = "unknown";

        if (this.hasChildren()) {
            // accumulate status across children
            HashMap<String, String> map = new HashMap<String, String>();
            // this should only ever be two layers deep
            for (Task childTask : this.getChildren()) {
                if (childTask.hasChildren()) {
                    for (Task childTask2 : childTask.getChildren()) {
                        if ((childTask2.getObjectAnnotations() != null) &&
                            (childTask2.getObjectAnnotations().size() > 0) &&
                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
                        }
                    }
                } else {
                    if ((childTask.getObjectAnnotations() != null) &&
                        (childTask.getObjectAnnotations().size() > 0) &&
                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
                    }
                }
            }
            // now, how do we report these?
            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
            for (String key : map.values()) {
                // task results
                if (!resultsMap.containsKey(key)) {
                    resultsMap.put(key, new Integer(1));
                } else {
                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
                }
            }
            status = resultsMap.toString();
        } else { // childless
            status = this.getStatus(myShepherd);
        }
        return status;
    }

    public boolean isFastlane(Shepherd myShepherd) {
        String status = "waiting to queue";
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);

        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
            return true;
        }
        return false;
    }

    public void setStatus(String newStatus) {
        setModified();
        if (newStatus == null) status = null;
        else { status = newStatus; }
    }

    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }

    // this will set all date stuff based on ms since epoch
    public void setCompletionDateInMilliseconds(Long ms) {
        this.completionDateInMilliseconds = ms;
    }

    // no arg = set to now
    public void setCompletionDateInMilliseconds() {
        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
    }

    // capture original queue message to make this Task more easily resumeable
    public String getQueueResumeMessage() { return queueResumeMessage; }
    public void setQueueResumeMessage(String message) {
        if (message == null) { queueResumeMessage = null; } else {
            queueResumeMessage = message;
        }
    }

    public JSONObject getMatchingSetFilter() {
        if (getParameters() == null) return null;
        return getParameters().optJSONObject("matchingSetFilter");
    }

    public JSONObject getIdentificationMethodInfo() {
        if (getParameters() == null) return null;
        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
        JSONObject rtn = new JSONObject();
        // vector/embed flavor
        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
            null) != null) {
            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
                "model_id", null);
            if (modelId == null) {
                rtn.put("description", "Vector embedding match");
            } else {
                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
                rtn.put("modelId", modelId);
            }
            return rtn;
        }
        // it seems both of these are in most logs (and are identical), but being safe in case there are
        // examples in the wild with only one
        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
            "query_config_dict");
        if (conf == null)
            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
                "queryConfigDict");
        // we set HotSpotter if pipeline_root is not set here
        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
        rtn.put("description",
            getParameters().getJSONObject("ibeis.identification").optString("description",
            "unknown algorithm/method"));
        return rtn;
    }

    // convenience
    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
        return myShepherd.getMatchResults(this);
    }

    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
        List<MatchResult> all = myShepherd.getMatchResults(this);

        if (Util.collectionIsEmptyOrNull(all)) return null;
        return all.get(0);
    }

    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
        List<MatchResult> mrs = new ArrayList<MatchResult>();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
            myShepherd);

        if (logs == null) return mrs;
        for (IdentityServiceLog log : logs) {
            JSONObject res = log.getJsonResult();
            // in theory this is how we can tell if it is an ident result log versus detection
            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
                try {
                    MatchResult mr = new MatchResult(log, myShepherd);
                    System.out.println("[INFO] generateMatchResults() [log t=" +
                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
                    myShepherd.getPM().makePersistent(mr);
                    mrs.add(mr);
                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
                        log.getTimestamp());
                } catch (java.io.IOException ex) {
                    System.out.println("[ERROR] generateMatchResults() [log t=" +
                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
                    ex.printStackTrace();
                    setStatusDetailsAddError("UNKNOWN",
                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
                        " failed due to: " + ex);
                }
            }
        }
        return mrs;
    }

    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", getId());
        rtn.put("parentTaskId", getParentId());
        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
        rtn.put("timeInactiveMillis", timeInactive());
        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
        // we should have it here, hence this debugging value ... possible optimization for later
        if (hasObjectAnnotations()) {
            JSONArray annotArr = new JSONArray();
            for (Annotation ann : getObjectAnnotations()) {
                if (ann != null) annotArr.put(ann.getId());
            }
            rtn.put("__taskAnnotations", annotArr);
        }
        JSONObject methodInfo = getIdentificationMethodInfo();
        // we basically use this to determine if we are "identification-like" enough
        // to display extended details
        if (methodInfo != null) {
            rtn.put("method", methodInfo);
            rtn.put("matchingSetFilter", getMatchingSetFilter());
/*
            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
               (as there may be non-leaf nodes with methodInfo)
 * note: we try getting it regardless of children ("just in case"); but only try to generate if none
            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
               as vector results should have generated their MatchResult upon completion
 */
            MatchResult mr = getLatestMatchResult(myShepherd);
            if ((mr == null) && !hasChildren()) {
                System.out.println(
                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
                    this.getId());
                List<MatchResult> mrs = generateMatchResults(myShepherd);
                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
                if (mrs.size() > 0) {
                    mr = mrs.get(mrs.size() - 1);
                    // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
                    rtn.put("_commitShepherd", true);
                }
            }
            if (mr != null)
                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
        }
        // now we recurse thru children if applicable
        if (hasChildren()) {
            JSONArray charr = new JSONArray();
            for (Task child : children) {
                // TODO decide if we need to process child????
                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
                // we have to bubble this up all the way to the toplevel  :/
                if (childJson.optBoolean("_commitShepherd", false))
                    rtn.put("_commitShepherd", true);
                charr.put(childJson);
            }
            rtn.put("children", charr);
            // if we dont have children (leaf nodes) we get the status
        } else {
            // unsure which of these two things is more accurate or useful; thus including both
            rtn.put("status", getStatus(myShepherd));
            rtn.put("statusOverall", getOverallStatus(myShepherd));
            rtn.put("statusDetails", getStatusDetails());
        }
        return rtn;
    }
}

 succeeded in 269ms:
package org.ecocean.api;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;

// note: this is for use on any non-Base object
// see api/BaseObject if object extends Base.java
public class GenericObject extends ApiBase {
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        // normally false for GET, but some deep behavior creates objects on-the-fly
        // and therefore needs to commit to db
        boolean commitShepherd = false;
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("api.GenericObject.doGet");
        myShepherd.beginDBTransaction();

        String uri = request.getRequestURI();
        String[] args = uri.substring(8).split("/");
        if (args.length < 1) throw new ServletException("bad path");
        JSONObject rtn = new JSONObject();
        rtn.put("success", false);
        try {
            User currentUser = myShepherd.getUser(request);
            switch (args[0]) {
            case "media-assets":
                if (currentUser == null) {
                    rtn.put("statusCode", 401);
                    rtn.put("error", "access denied");
                } else {
                    MediaAsset ma = null;
                    URL url = null;
                    try {
                        ma = MediaAssetFactory.load(Integer.parseInt(args[1]), myShepherd);
                        if (ma != null) url = ma.safeURL(myShepherd, request);
                    } catch (Exception ex) {
                        throw new ApiException(ex.toString());
                    }
                    if (ma == null) {
                        rtn.put("statusCode", 404);
                        rtn.put("error", "not found");
                    } else {
                        rtn.put("success", true);
                        rtn.put("statusCode", 200);
                        rtn.put("url", url.toString());
                        rtn.put("width", ma.getWidth());
                        rtn.put("height", ma.getHeight());
                        rtn.put("rotationInfo", ma.getRotationInfo());
                        JSONArray janns = new JSONArray();
                        for (Annotation ann : ma.getAnnotations()) {
                            JSONObject jann = new JSONObject();
                            if (ann.getFeatures() != null) {
                                for (Feature ft : ann.getFeatures()) {
                                    if (ft.isUnity()) {
                                        jann.put("trivial", true);
                                        jann.put("x", 0);
                                        jann.put("y", 0);
                                        jann.put("width", (int)ma.getWidth());
                                        jann.put("height", (int)ma.getHeight());
                                    } else {
                                        // basically if we have more than one feature, only one wins
                                        if (ft.getParameters() != null) jann = ft.getParameters();
                                    }
                                }
                            }
                            Encounter enc = ann.findEncounter(myShepherd);
                            if (enc != null) {
                                jann.put("encounterId", enc.getId());
                                jann.put("encounterTaxonomy", enc.getTaxonomyString());
                            }
                            jann.put("id", ann.getId());
                            janns.put(jann);
                        }
                        rtn.put("annotations", janns);
                    }
                }
                break;
            case "tasks":
                if (currentUser == null) {
<?xml version="1.0" encoding="utf-8"?>
<web-app xmlns="http://java.sun.com/xml/ns/javaee"
		 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
		 version="3.0"
		 metadata-complete="true">

	<display-name>Wildbook®</display-name>
	<description>Photo-identification Library</description>

	<session-config>
		<session-timeout>30</session-timeout>
	</session-config>

	<listener>
		<description>ServletContextListener</description>
		<listener-class>org.ecocean.StartupWildbook</listener-class>
	</listener>

	<filter>
		<filter-name>UTF8Filter</filter-name>
		<filter-class>org.ecocean.servlet.StrutsUTF8Filter</filter-class>
	</filter>

	<filter-mapping>
		<filter-name>UTF8Filter</filter-name>
		<url-pattern>/*</url-pattern>
	</filter-mapping>

	<filter>
		<description>Dynamic Image Filter</description>
		<filter-name>ImgFilter</filter-name>
		<filter-class>com.sunwesttek.image.servlet.ImgFilter</filter-class>
		<init-param>
			<param-name>maxSyncServlets</param-name>
			<param-value>2</param-value>
		</init-param>
	</filter>

	<filter-mapping>
		<filter-name>ImgFilter</filter-name>
		<url-pattern>/di/ImgFilter/*</url-pattern>
	</filter-mapping>

	<servlet>
		<servlet-name>ReactAppServlet</servlet-name>
		<servlet-class>org.ecocean.servlet.ReactAppServlet</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ReactAppServlet</servlet-name>
		<url-pattern>/react</url-pattern>
	</servlet-mapping>

	<!--
	  SPA fallback: route unmatched paths through ReactAppServlet
	  (mapped at /react) rather than serving /react/index.html
	  directly. The servlet explicitly resets the response status
	  to 200 so PWA service workers (NetworkFirst caching) accept
	  the body. Forwarding to /react/index.html directly preserved
	  Tomcat's 404 status, which Workbox rejects as no-response,
	  blanking the React match-results page.
	-->
	<error-page>
		<error-code>404</error-code>
		<location>/react</location>
	</error-page>

	<servlet>
		<servlet-name>jsp</servlet-name>
		<servlet-class>org.apache.jasper.servlet.JspServlet</servlet-class>
		<init-param>
			<param-name>mappedfile</param-name>
			<param-value>false</param-value>
		</init-param>
	</servlet>

	<filter>
		<filter-name>ShiroFilter</filter-name>
		<filter-class>org.apache.shiro.web.servlet.IniShiroFilter</filter-class>
		<init-param>
			<param-name>config</param-name>
			<param-value>
				[main]
				realmA = org.ecocean.security.ShepherdRealm
				roles.unauthorizedUrl = /accessDenied.jsp
				authcBasicWildbook = org.ecocean.security.WildbookBasicHttpAuthenticationFilter
				authc.loginUrl = /react/login

				[filters]

				[urls]
				# NOTE: Rules are processed in order - first match wins
				# This configuration is from web-reorganized.xml (consolidation of duplicates)

				# ===== REST =====
				/rest/** = authcBasicWildbook,roles[researcher]
				#/xxxapi/** = authcBasicWildbook,roles[rest-readonly]
				/LightRest/** = authcBasicWildbook,roles[researcher]

				# ===== v3 API Security =====
				/api/v3/bulk-import = authc
				/api/v3/bulk-import/** = authc
				/api/v3/bulk-export = authc
				/api/v3/bulk-export/** = authc

				# ===== Appadmin =====
				/appadmin/dataIntegrity.jsp = authc
				/appadmin/iaBreakdownBySpecies.jsp = authc, roles[researcher]
				/appadmin/sharedAnnotations.jsp = authc
				/appadmin/duplicateAnnotations.jsp = authc
				/appadmin/dataValuesCheck.jsp = authc
				/appadmin/scanTaskAdmin.jsp = authc, roles[researcher]
				/appadmin/users.jsp = authc, roles[orgAdmin]
				/appadmin/automatedUserReconciliation.jsp = authc, roles[admin]
				/appadmin/iaSpeciesDiff.jsp = authcBasicWildbook,roles[rest]
				/appadmin/resendBulkImportID.jsp = authc, roles[researcher]
				/appadmin/consolidateDuplicateUsers.jsp = authc, roles[admin]
				/appadmin/consolidATEDuplicateUsersChecker.jsp = authc, roles[admin]
				/appadmin/consolidateDuplicateUsersSandbox.jsp = authc, roles[admin]

 succeeded in 563ms:
src/main/webapp/WEB-INF/web.xml:102:				/api/v3/bulk-import = authc
src/main/webapp/WEB-INF/web.xml:103:				/api/v3/bulk-import/** = authc
src/main/webapp/WEB-INF/web.xml:104:				/api/v3/bulk-export = authc
src/main/webapp/WEB-INF/web.xml:105:				/api/v3/bulk-export/** = authc
src/main/webapp/WEB-INF/web.xml:394:		<url-pattern>/api/v3/login</url-pattern>
src/main/webapp/WEB-INF/web.xml:404:		<url-pattern>/api/v3/logout</url-pattern>
src/main/webapp/WEB-INF/web.xml:414:		<url-pattern>/api/v3/user/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:424:		<url-pattern>/api/v3/home</url-pattern>
src/main/webapp/WEB-INF/web.xml:434:		<url-pattern>/api/v3/encounters/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:437:		<servlet-name>ApiGenericObject</servlet-name>
src/main/webapp/WEB-INF/web.xml:438:		<url-pattern>/api/v3/tasks/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:443:		<url-pattern>/api/v3/individuals/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:448:		<url-pattern>/api/v3/occurrences/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:453:		<url-pattern>/api/v3/annotations/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:457:		<servlet-name>ApiGenericObject</servlet-name>
src/main/webapp/WEB-INF/web.xml:458:		<servlet-class>org.ecocean.api.GenericObject</servlet-class>
src/main/webapp/WEB-INF/web.xml:462:		<servlet-name>ApiGenericObject</servlet-name>
src/main/webapp/WEB-INF/web.xml:463:		<url-pattern>/api/v3/media-assets/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:473:		<url-pattern>/api/v3/individuals/info/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:483:		<url-pattern>/api/v3/projects/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:493:		<url-pattern>/api/v3/search/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:503:		<url-pattern>/api/v3/site-settings</url-pattern>
src/main/webapp/WEB-INF/web.xml:508:		<url-pattern>/api/v3/site-settings/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:518:		<url-pattern>/api/v3/bulk-import</url-pattern>
src/main/webapp/WEB-INF/web.xml:523:		<url-pattern>/api/v3/bulk-import/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:533:		<url-pattern>/api/v3/bulk-export/*</url-pattern>
src/main/webapp/WEB-INF/web.xml:544:		<url-pattern>/api/v3/docs</url-pattern>
src/main/webapp/WEB-INF/web.xml:549:		<url-pattern>/api/v3/docs/openapi.yaml</url-pattern>
src/main/java/org/ecocean/api/GenericObject.java:27:public class GenericObject extends ApiBase {
src/main/java/org/ecocean/api/GenericObject.java:28:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/GenericObject.java:36:        myShepherd.setAction("api.GenericObject.doGet");
src/main/java/org/ecocean/api/SearchApi.java:18:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/BaseObject.java:40:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetLivingStatus.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/Login.java:38:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/MarkedIndividualInfo.java:23:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/UserInfo.java:42:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/UserHome.java:25:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterForm.java:71:    @Override public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetBehavior.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/Logout.java:26:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/WriteOutScanTask.java:32:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/Projects.java:16:    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/GenericObject.java:27:public class GenericObject extends ApiBase {
src/main/java/org/ecocean/api/GenericObject.java:28:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/GenericObject.java:36:        myShepherd.setAction("api.GenericObject.doGet");
src/main/java/org/ecocean/servlet/DontTrack.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/SiteSettings.java:45:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/SiteSettings.java:51:        myShepherd.setAction("api.SiteSettings.doGet");
src/main/java/org/ecocean/servlet/EncounterSetLifeStage.java:16:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterDelete.java:35:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/AddOccToTrack.java:20:    @Override public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/AnnotationEdit.java:30:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/WorkspacesForUser.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetAlternateID.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/AddSurveyTrackToSurvey.java:24:    @Override public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/DispatchServlet.java:109:    @Override public void doGet(HttpServletRequest req, HttpServletResponse res)
src/main/java/org/ecocean/api/ApiDocsServlet.java:14:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/ApiDocsServlet.java:60:        String specUrl = contextPath + "/api/v3/docs/openapi.yaml";
src/main/java/org/ecocean/servlet/AnnotationSetMatchAgainst.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetIdentifiable.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterCreateSurvey.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/Collaborate.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/AddLabeledKeyword.java:33:   public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
src/main/java/org/ecocean/servlet/EncounterResetDate.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/WorkspaceServer.java:41:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/WorkspaceServer.java:66:                System.out.println("doGet successfully grabbed workspace with id=" +
src/main/java/org/ecocean/servlet/EncounterAddSpotFile.java:38:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/BulkImport.java:35:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/api/BulkImport.java:41:        myShepherd.setAction("api.Bulk.doGet");
src/main/java/org/ecocean/servlet/EncounterAddUser.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetGPS.java:17:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterAddComment.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/WorkspaceDelete.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterRemoveUser.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterAddImage.java:34:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterRemoveImage.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterRemoveTissueSample.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetDynamicProperty.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetCountry.java:16:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetGenusSpecies.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualAddEncounter.java:28:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetField.java:20:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetSubmitterID.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserSelfUpdate.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceCreate.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetTissueSample.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:36:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetOccurrenceRemarks.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/LoginUser.java:32:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IAGlobals.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MediaAssetModify.java:38:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SiteSearch.java:35:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/IndividualAddComment.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetString.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceAddEncounter.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserResetPasswordSendEmail.java:28:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetTapirLinkExposure.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetObservation.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IAGateway.java:51:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IAGateway.java:69:        // duplicated in both doGet and doPost
src/main/java/org/ecocean/servlet/LightRestServlet.java:156:    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
src/main/java/org/ecocean/servlet/LightRestServlet.java:158:        System.out.println("        LIGHTREST: doGet called");
src/main/java/org/ecocean/servlet/MediaAssetCreate.java:37:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SinglePhotoVideoTransform.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ImportTaskDetection.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetState.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceAddComment.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserResetPassword.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetMeasurements.java:25:    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp)
src/main/java/org/ecocean/servlet/GrothMatchServlet.java:42:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/KeywordHandler.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MediaAssetContext.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SessionExtend.java:10:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetSex.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserResetAcceptedUserAgreement.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MetricsResource.java:15:@Path("TestPrometheusClient") @Produces(MediaType.TEXT_PLAIN) public class MetricsResource {
src/main/java/org/ecocean/servlet/export/SonicPiFormat.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetSurveyAndTrack.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetMaximumElevation.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/GridHeartbeatReceiver.java:20:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/JavascriptGlobals.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MediaAssetAttach.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetScarring.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MergeIndividual.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserRemoveProfileImage.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/SOCPROGExport.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/ExportExcelFile.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetMaximumDepth.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/ImportIA.java:37:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:50:        doGet(request, response);
src/main/java/org/ecocean/servlet/InterconnectSubmitSpots.java:53:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/WebImport.java:60:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MassSwapLocationCode.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ScheduledIndividualMergeUpdate.java:28:        doGet(request, response);
src/main/java/org/ecocean/servlet/ScheduledIndividualMergeUpdate.java:31:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/MembershipDelete.java:28:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserPreferences.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java:34:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetMatchedBy.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetYearOfDeath.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ScanWorkItemResultsHandler.java:60:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/ImportExcelMetadata.java:50:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MassSetLocationCodeFromLocationString.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/UpdateStandard.java:71:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MembershipCreate.java:33:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetPatterningPassport.java:39:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserInfo.java:20:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/OccurrenceSearchExportMetadataExcel.java:63:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportSocialNetwork.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetLocationID.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetYearOfBirth.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ScanResultsServlet.java:45:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/ImportAccess.java:148:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MassExposeGBIF.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/MediaAssetsForUser.java:35:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetPatterningCode.java:16:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserGetSimpleJSON.java:32:        doGet(request, response);
src/main/java/org/ecocean/servlet/UserGetSimpleJSON.java:35:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/importer/StandardImport.java:46:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportEmailAddresses.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportShapefile.java:41:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/OccurrenceSearchExportGtm.java:87:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetLocation.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetSex.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ScanAppletSupport.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/FixStandardImport.java:53:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/LogoutUser.java:29:     * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/LogoutUser.java:31:    protected void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportGeneGISFormat.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ProjectDelete.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserGetNotifications.java:30:        doGet(request, response);
src/main/java/org/ecocean/servlet/UserGetNotifications.java:33:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportMetadataExcel.java:92:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterVMData.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetNickName.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ResurrectDeletedEncounter.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceSetMetadataField.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportExcelFile.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ProjectCreate.java:34:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserDelete.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/importer/ImportSRGD.java:42:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterAnnotationExportExcelFile.java:18:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/EncounterSearchExportKML.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/EncounterSetVerbatimEventDate.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java:37:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetName.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualCreateForProject.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/RestServlet.java:162:    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
src/main/java/org/ecocean/servlet/export/GetEncounterSearchGoogleMapsPoints.java:37:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OrganizationGet.java:32:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserCreate.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualRemoveEncounter.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/GenalexExportCodominantMSDataBySize.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/ExportExcelIndividualReport.java:25:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/KinalyzerExport.java:26:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualSetAlternateID.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualAddIncrementalProjectId.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceSetIndividualCount.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OrganizationEdit.java:35:    @Override public void doGet(final HttpServletRequest request,
src/main/java/org/ecocean/servlet/export/GenePopExport.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualRemoveDataFile.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserConsolidate.java:33:    @Override public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/export/GetIndividualSearchGoogleMapsPoints.java:37:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/IndividualAddFile.java:34:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceRemoveEncounter.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceSetGroupBehavior.java:22:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/RemoveDynamicProperty.java:29:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceSetObservation.java:30:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/OccurrenceSetSurveyAndTrack.java:31:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ReactAppServlet.java:74:     * Override {@code service} (not just {@code doGet}) so non-GET
src/main/java/org/ecocean/servlet/RelationshipCreate.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ProjectIA.java:39:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ProjectUpdate.java:33:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserCheck.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ProjectGet.java:33:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/ReCAPTCHA.java:40:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/RelationshipDelete.java:21:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleRemoveMicrosatelliteMarkers.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleSetSexAnalysis.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleSetHaplotype.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleRemoveSexAnalysis.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleSetMeasurement.java:21:    public void doGet(HttpServletRequest req, HttpServletResponse resp)
src/main/java/org/ecocean/servlet/TissueSampleSetMicrosatelliteMarkers.java:24:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SubmitSurvey.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TranslateQuery.java:55:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/UserAddProfileImage.java:34:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java:40:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SurveySetObservation.java:27:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleRemoveHaplotype.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TranslationsGet.java:32:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SubmitSpotsAndTransformImage.java:33:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TrackerFeed.java:33:    public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/SurveyCreate.java:24:    @Override public void doGet(HttpServletRequest request, HttpServletResponse response)
src/main/java/org/ecocean/servlet/TissueSampleRemoveBiologicalMeasurement.java:23:    public void doGet(HttpServletRequest request, HttpServletResponse response)

exec
/bin/bash -lc 'rg -n "BrowserRouter|HashRouter|basename|AuthenticatedSwitch|serviceWorker|NetworkFirst|registerRoute|PUBLIC_URL|homepage" frontend/src frontend/package.json frontend/public' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' frontend/src/App.jsx 2>/dev/null || true" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,180p' frontend/src/index.jsx 2>/dev/null || sed -n '1,180p' frontend/src/index.js 2>/dev/null" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '90,130p' src/main/webapp/WEB-INF/web.xml && sed -n '380,466p' src/main/webapp/WEB-INF/web.xml" in /mnt/c/Wildbook-clean2
 succeeded in 261ms:
import React, { useState, useRef } from "react";
import { IntlProvider } from "react-intl";
import messagesEn from "./locale/en.json";
import messagesEs from "./locale/es.json";
import messagesFr from "./locale/fr.json";
import messagesIt from "./locale/it.json";
import messagesDe from "./locale/de.json";
import "bootstrap/dist/css/bootstrap.min.css";
import "bootstrap-icons/font/bootstrap-icons.css";
import { QueryClient, QueryClientProvider } from "react-query";
import FrontDesk from "./FrontDesk";
import { BrowserRouter } from "react-router-dom";
import LocaleContext from "./IntlProvider";
import FooterVisibilityContext from "./FooterVisibilityContext";
import Cookies from "js-cookie";
import FilterContext from "./FilterContextProvider";
import { SiteSettingsProvider } from "./SiteSettingsContext";
import { ToastContainer } from "react-toastify";
import "react-toastify/dist/ReactToastify.css";

function App() {
  const messageMap = {
    en: messagesEn,
    es: messagesEs,
    fr: messagesFr,
    it: messagesIt,
    de: messagesDe,
  };

  const initialLocale = Cookies.get("wildbookLangCode") || "en";
  const [locale, setLocale] = useState(initialLocale);

  const [visible, setVisible] = useState(true);

  const containerStyle = {
    display: "flex",
    flexDirection: "column",
    minHeight: "100vh",
  };

  const queryClientRef = useRef(null);
  if (!queryClientRef.current) queryClientRef.current = new QueryClient();

  const handleLocaleChange = (newLocale) => {
    setLocale(newLocale);
    Cookies.set("wildbookLangCode", newLocale);
  };

  const [filters, setFilters] = useState({});
  const updateFilter = (filterName, value) => {
    setFilters((prevFilters) => ({
      ...prevFilters,
      [filterName]: value,
    }));
  };

  const resetFilters = () => {
    setFilters({});
  };

  const publicUrl = process.env.PUBLIC_URL
    ? process.env.PUBLIC_URL.startsWith("http")
      ? new URL(process.env.PUBLIC_URL).pathname
      : process.env.PUBLIC_URL
    : "/";

  return (
    <QueryClientProvider client={queryClientRef.current}>
      <LocaleContext.Provider
        value={{ locale, onLocaleChange: handleLocaleChange }}
      >
        <div
          className="App mx-auto w-100 position-relative"
          style={containerStyle}
        >
          <BrowserRouter basename={publicUrl}>
            <IntlProvider
              locale={locale}
              defaultLocale="en"
              messages={messageMap[locale]}
            >
              <SiteSettingsProvider>
                <FooterVisibilityContext.Provider
                  value={{ visible, setVisible }}
                >
                  <FilterContext.Provider
                    value={{ filters, updateFilter, resetFilters }}
                  >
                    <FrontDesk
                      adminUserInitialized={true}
                      setLocale={setLocale}
                    />

                    <ToastContainer
                      position="top-right"
                      autoClose={3000}
                      hideProgressBar={false}
                      newestOnTop={false}
                      closeOnClick
                      rtl={false}
                      pauseOnFocusLoss
                      draggable
                      pauseOnHover
                    />
                  </FilterContext.Provider>
                </FooterVisibilityContext.Provider>
              </SiteSettingsProvider>
            </IntlProvider>
          </BrowserRouter>
        </div>
      </LocaleContext.Provider>
    </QueryClientProvider>
  );
}

export default App;

 succeeded in 255ms:
import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import * as serviceWorkerRegistration from "./serviceWorkerRegistration";
import reportWebVitals from "./reportWebVitals";

const root = ReactDOM.createRoot(document.getElementById("root"));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);

// Service worker registration with auto-update on new versions
// When a new version is detected, it will automatically reload the page

let reloaded = false;

if ("serviceWorker" in navigator) {
  navigator.serviceWorker.addEventListener("controllerchange", () => {
    if (reloaded) return;
    reloaded = true;
    if (window.__WB_SW_RELOADED__) return;
    window.__WB_SW_RELOADED__ = true;
    window.location.reload();
  });
}

serviceWorkerRegistration.register({
  onUpdate: (registration) => {
    console.log("New Wildbook version available!");
    // Skip waiting and take control immediately
    if (registration.waiting) {
      registration.waiting.postMessage({ type: "SKIP_WAITING" });
    }
  },
  onSuccess: () => {
    console.log("Wildbook is ready for offline use.");
  },
});

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();

 succeeded in 404ms:
				[filters]

				[urls]
				# NOTE: Rules are processed in order - first match wins
				# This configuration is from web-reorganized.xml (consolidation of duplicates)

				# ===== REST =====
				/rest/** = authcBasicWildbook,roles[researcher]
				#/xxxapi/** = authcBasicWildbook,roles[rest-readonly]
				/LightRest/** = authcBasicWildbook,roles[researcher]

				# ===== v3 API Security =====
				/api/v3/bulk-import = authc
				/api/v3/bulk-import/** = authc
				/api/v3/bulk-export = authc
				/api/v3/bulk-export/** = authc

				# ===== Appadmin =====
				/appadmin/dataIntegrity.jsp = authc
				/appadmin/iaBreakdownBySpecies.jsp = authc, roles[researcher]
				/appadmin/sharedAnnotations.jsp = authc
				/appadmin/duplicateAnnotations.jsp = authc
				/appadmin/dataValuesCheck.jsp = authc
				/appadmin/scanTaskAdmin.jsp = authc, roles[researcher]
				/appadmin/users.jsp = authc, roles[orgAdmin]
				/appadmin/automatedUserReconciliation.jsp = authc, roles[admin]
				/appadmin/iaSpeciesDiff.jsp = authcBasicWildbook,roles[rest]
				/appadmin/resendBulkImportID.jsp = authc, roles[researcher]
				/appadmin/consolidateDuplicateUsers.jsp = authc, roles[admin]
				/appadmin/consolidATEDuplicateUsersChecker.jsp = authc, roles[admin]
				/appadmin/consolidateDuplicateUsersSandbox.jsp = authc, roles[admin]
				/appadmin/kwAdmin.jsp = authc, roles[admin]
				/appadmin/batch* = authc, roles[admin]
				/appadmin/** = authc, roles[admin]

				# ===== Import Operations =====
				/import/** = authc, roles[researcher]
				/ImportSRGD = authc, roles[admin]
				/ImportAccess = authc, roles[admin]
				/StandardImport = authc, roles[admin]
				/FixStandardImport = authc, roles[admin]
	</filter>

	<filter-mapping>
		<filter-name>ShiroFilter</filter-name>
		<url-pattern>/*</url-pattern>
	</filter-mapping>

	<servlet>
		<servlet-name>ApiLogin</servlet-name>
		<servlet-class>org.ecocean.api.Login</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiLogin</servlet-name>
		<url-pattern>/api/v3/login</url-pattern>
	</servlet-mapping>

	<servlet>
		<servlet-name>ApiLogout</servlet-name>
		<servlet-class>org.ecocean.api.Logout</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiLogout</servlet-name>
		<url-pattern>/api/v3/logout</url-pattern>
	</servlet-mapping>

	<servlet>
		<servlet-name>ApiUserInfo</servlet-name>
		<servlet-class>org.ecocean.api.UserInfo</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiUserInfo</servlet-name>
		<url-pattern>/api/v3/user/*</url-pattern>
	</servlet-mapping>

	<servlet>
		<servlet-name>ApiUserHome</servlet-name>
		<servlet-class>org.ecocean.api.UserHome</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiUserHome</servlet-name>
		<url-pattern>/api/v3/home</url-pattern>
	</servlet-mapping>

	<servlet>
		<servlet-name>ApiBaseObject</servlet-name>
		<servlet-class>org.ecocean.api.BaseObject</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiBaseObject</servlet-name>
		<url-pattern>/api/v3/encounters/*</url-pattern>
	</servlet-mapping>
	<servlet-mapping>
		<servlet-name>ApiGenericObject</servlet-name>
		<url-pattern>/api/v3/tasks/*</url-pattern>
	</servlet-mapping>

	<servlet-mapping>
		<servlet-name>ApiBaseObject</servlet-name>
		<url-pattern>/api/v3/individuals/*</url-pattern>
	</servlet-mapping>

	<servlet-mapping>
		<servlet-name>ApiBaseObject</servlet-name>
		<url-pattern>/api/v3/occurrences/*</url-pattern>
	</servlet-mapping>

	<servlet-mapping>
		<servlet-name>ApiBaseObject</servlet-name>
		<url-pattern>/api/v3/annotations/*</url-pattern>
	</servlet-mapping>

	<servlet>
		<servlet-name>ApiGenericObject</servlet-name>
		<servlet-class>org.ecocean.api.GenericObject</servlet-class>
	</servlet>

	<servlet-mapping>
		<servlet-name>ApiGenericObject</servlet-name>
		<url-pattern>/api/v3/media-assets/*</url-pattern>
	</servlet-mapping>

	<servlet>

 succeeded in 863ms:
frontend/public/index.html:9:    <!-- <link rel="icon" href="%PUBLIC_URL%/favicon.ico" /> -->
frontend/public/index.html:13:    <!-- <link rel="apple-touch-icon" href="%PUBLIC_URL%/logo192.png" /> -->
frontend/public/index.html:18:    <link rel="manifest" href="%PUBLIC_URL%/manifest.json" />
frontend/public/index.html:23:      Notice the use of %PUBLIC_URL% in the tags above.
frontend/public/index.html:27:      Unlike "/favicon.ico" or "favicon.ico", "%PUBLIC_URL%/favicon.ico" will
frontend/src/service-worker.js:13:import { registerRoute } from "workbox-routing";
frontend/src/service-worker.js:15:import { NetworkFirst } from "workbox-strategies";
frontend/src/service-worker.js:34:registerRoute(
frontend/src/service-worker.js:52:  createHandlerBoundToURL(process.env.PUBLIC_URL + "/index.html"),
frontend/src/service-worker.js:57:registerRoute(
frontend/src/service-worker.js:73:registerRoute(
frontend/src/service-worker.js:75:  new NetworkFirst(),
frontend/src/index.js:5:import * as serviceWorkerRegistration from "./serviceWorkerRegistration";
frontend/src/index.js:20:if ("serviceWorker" in navigator) {
frontend/src/index.js:21:  navigator.serviceWorker.addEventListener("controllerchange", () => {
frontend/src/index.js:30:serviceWorkerRegistration.register({
frontend/src/pages/Login.jsx:55:            src={`${process.env.PUBLIC_URL}/images/signin.png`}
frontend/src/pages/Login.jsx:201:                        href={`${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`}
frontend/src/pages/Login.jsx:214:                        href={`${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`}
frontend/src/pages/HowToPhotograph.jsx:9:  const pdfUrl = `${process.env.PUBLIC_URL || ""}/files/how-to-photograph.pdf`;
frontend/src/utils/utils.js:3:import { BrowserRouter } from 'react-router-dom';
frontend/src/utils/utils.js:28:  return render(ui, { wrapper: BrowserRouter });
frontend/src/__tests__/setupTests.js:21:process.env.PUBLIC_URL = "";
frontend/src/FrontDesk.jsx:2:import UnauthenticatedSwitch from "./UnAuthenticatedSwitch";
frontend/src/FrontDesk.jsx:3:import AuthenticatedSwitch from "./AuthenticatedSwitch";
frontend/src/FrontDesk.jsx:104:        <AuthenticatedSwitch
frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx:45:  `${process.env.PUBLIC_URL || ""}${p.startsWith("/") ? p : `/${p}`}`;
frontend/src/constants/navMenu.js:19:        href: `${process.env.PUBLIC_URL}/report`,
frontend/src/constants/navMenu.js:41:        href: `${process.env.PUBLIC_URL}/bulk-import`,
frontend/src/constants/navMenu.js:71:        href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
frontend/src/constants/navMenu.js:75:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
frontend/src/constants/navMenu.js:79:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`,
frontend/src/constants/navMenu.js:83:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
frontend/src/constants/navMenu.js:112:        href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}`,
frontend/src/constants/navMenu.js:121:            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=approved`,
frontend/src/constants/navMenu.js:130:            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=unapproved`,
frontend/src/constants/navMenu.js:139:            href: `${process.env.PUBLIC_URL}/encounter-search?username=${username}&state=unidentifiable`,
frontend/src/constants/navMenu.js:208:        href: `${process.env.PUBLIC_URL}/encounter-search`,
frontend/src/constants/navMenu.js:319:        href: `${process.env.PUBLIC_URL}/report`,
frontend/src/constants/navMenu.js:362:        href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
frontend/src/constants/navMenu.js:366:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`,
frontend/src/constants/navMenu.js:370:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`,
frontend/src/constants/navMenu.js:374:            href: `${process.env.PUBLIC_URL}/policies-and-data?section=citing_wildbook`,
frontend/src/pages/AdminLogs.jsx:40:        backgroundImage: `url(${process.env.PUBLIC_URL}/images/List_of_Logs_Image.png)`,
frontend/src/pages/errorPages/ErrorPage.jsx:47:        backgroundImage: `url("${process.env.PUBLIC_URL}/images/${errorCode}_background.png")`,
frontend/src/pages/errorPages/ErrorPage.jsx:114:                  link={loginRequired ? `${process.env.PUBLIC_URL}/login` : `${process.env.PUBLIC_URL}/home`}
frontend/src/pages/errorPages/ErrorPage.jsx:265:              href={`${process.env.PUBLIC_URL}/images/${errorCode}_animal.png`}
frontend/src/components/UnAuthenticatedAppHeader.jsx:123:              href={`${process.env.PUBLIC_URL}/login`}
frontend/src/UnAuthenticatedSwitch.jsx:23:export default function UnAuthenticatedSwitch({
frontend/src/pages/SearchPages/EncounterSearch.jsx:295:        backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)), url('${process.env.PUBLIC_URL}/images/encounter_search_background.png')`,
frontend/src/pages/BulkImport/BulkImportImageUpload.jsx:192:              href={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
frontend/src/models/auth/useLogin.js:36:      ? `${process.env.PUBLIC_URL}${decodedRedirect}${location.hash}`
frontend/src/models/auth/useLogin.js:42:        let url = nextLocation || `${process.env.PUBLIC_URL}/home`;
frontend/src/pages/BulkImport/BulkImportSuccessModal.jsx:13:    window.location.href = `${process.env.PUBLIC_URL}/bulk-import-task?id=${submissionId}`;
frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx:162:          src={`${process.env.PUBLIC_URL}/images/MatchResultExample.png`}
frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx:319:                link={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx:420:              backgroundImage: `linear-gradient(rgba(0, 0, 0, 0.5), rgba(0, 0, 0, 0.5)),url(${process.env.PUBLIC_URL}/images/report_an_encounter.png)`,
frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx:476:                        href={`${process.env.PUBLIC_URL}/policies-and-data?section=terms_of_use`}
frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx:489:                        href={`${process.env.PUBLIC_URL}/policies-and-data?section=privacy_policy`}
frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx:334:              href={`${process.env.PUBLIC_URL}/login?redirect=%2Freport`}
frontend/src/__tests__/pages/PoliciesAndData.test.js:379:    test("handles PUBLIC_URL environment variable", async () => {
frontend/src/__tests__/pages/PoliciesAndData.test.js:380:      const originalEnv = process.env.PUBLIC_URL;
frontend/src/__tests__/pages/PoliciesAndData.test.js:381:      process.env.PUBLIC_URL = "/app";
frontend/src/__tests__/pages/PoliciesAndData.test.js:394:      process.env.PUBLIC_URL = originalEnv;
frontend/src/__tests__/FrontDesk.test.js:7:jest.mock("../AuthenticatedSwitch", () => {
frontend/src/__tests__/FrontDesk.test.js:9:  MockComponent.displayName = "MockAuthenticatedSwitch";
frontend/src/__tests__/FrontDesk.test.js:13:jest.mock("../UnAuthenticatedSwitch", () => {
frontend/src/__tests__/FrontDesk.test.js:15:  MockComponent.displayName = "MockUnAuthenticatedSwitch";
frontend/src/__tests__/pages/login/useLoginOpenRedirectProtection.test.js:60:      expect(window.location.href).toBe(`${process.env.PUBLIC_URL}/home`);
frontend/src/__tests__/App.test.js:40:  test("handles PUBLIC_URL fallback", () => {
frontend/src/__tests__/App.test.js:41:    delete process.env.PUBLIC_URL;
frontend/src/__tests__/pages/BulkImport/BulkImportSuccessModal.test.js:107:      `${process.env.PUBLIC_URL}/bulk-import-task?id=abc123`,
frontend/src/__tests__/pages/LandingPage/Reports.test.js:7:  process.env.PUBLIC_URL = "/react";
frontend/src/__tests__/pages/login/useLoginAuthenticateSuccess.test.js:59:      expect(window.location.href).toBe(`${process.env.PUBLIC_URL}/home`);
frontend/src/__tests__/pages/login/useLoginAuthenticateSuccess.test.js:84:      `${process.env.PUBLIC_URL}/dashboard`,
frontend/src/__tests__/pages/login/useLoginAuthenticateSuccess.test.js:108:    expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/admin`);
frontend/src/__tests__/pages/login/useLoginAuthenticateSuccess.test.js:128:    expect(window.location.href).toContain(`${process.env.PUBLIC_URL}`);
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:3:import { BrowserRouter } from "react-router-dom";
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:4:import UnAuthenticatedSwitch from "../../UnAuthenticatedSwitch";
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:53:describe("UnAuthenticatedSwitch", () => {
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:56:      <BrowserRouter>
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:57:        <UnAuthenticatedSwitch {...props} />
frontend/src/__tests__/components/UnauthenticatedSwitch.test.js:58:      </BrowserRouter>,
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:3:import { BrowserRouter } from "react-router-dom";
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:4:import AuthenticatedSwitch from "../../AuthenticatedSwitch";
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:56:describe("AuthenticatedSwitch", () => {
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:65:      <BrowserRouter>
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:66:        <AuthenticatedSwitch {...props} />
frontend/src/__tests__/components/AuthenticatedSwitch.test.js:67:      </BrowserRouter>,
frontend/src/AuthenticatedSwitch.jsx:41:export default function AuthenticatedSwitch({
frontend/src/AuthenticatedSwitch.jsx:50:    data?.imageURL || `${process.env.PUBLIC_URL}/images/Avatar.png`;
frontend/src/App.jsx:12:import { BrowserRouter } from "react-router-dom";
frontend/src/App.jsx:61:  const publicUrl = process.env.PUBLIC_URL
frontend/src/App.jsx:62:    ? process.env.PUBLIC_URL.startsWith("http")
frontend/src/App.jsx:63:      ? new URL(process.env.PUBLIC_URL).pathname
frontend/src/App.jsx:64:      : process.env.PUBLIC_URL
frontend/src/App.jsx:76:          <BrowserRouter basename={publicUrl}>
frontend/src/App.jsx:109:          </BrowserRouter>
frontend/src/__tests__/components/header/MultiLanguageDropdown.test.js:10:  process.env.PUBLIC_URL = "/react";
frontend/src/components/filterFields/ApplyQueryFilter.jsx:50:              window.location.href = `${process.env.PUBLIC_URL}/encounter-search?searchQueryId=${queryId}`;
frontend/src/components/SessionWarning.jsx:46:      window.open(`${process.env.PUBLIC_URL}/login/`, "_blank");
frontend/src/components/home/LatestData.jsx:28:          href={`${process.env.PUBLIC_URL}/encounter-search?username=${username}`}
frontend/src/components/home/Report.jsx:34:            href={`${process.env.PUBLIC_URL}/images/submit.png`}
frontend/src/components/home/Report.jsx:55:          <BrutalismButton link={`${process.env.PUBLIC_URL}/bulk-import`}>
frontend/src/components/home/PickUpWhereYouLeft.jsx:78:          backgroundImage: `url(${process.env.PUBLIC_URL}/images/pick.png)`,
frontend/src/components/home/LandingImage.jsx:9:        backgroundImage: `url('${process.env.PUBLIC_URL}/images/forest.png')`,
frontend/src/components/navBar/MultiLanguageDropdown.jsx:25:            src={`${process.env.PUBLIC_URL}/flags/${flag}.png`}
frontend/src/components/navBar/MultiLanguageDropdown.jsx:44:                src={`${process.env.PUBLIC_URL}/flags/${localeMap[locale]}.png`}
frontend/src/components/header/AvatarAndUserProfile.jsx:46:        <NavDropdown.Item href={`${process.env.PUBLIC_URL}/home/`} style={{ color: "black" }}>

exec
/bin/bash -lc "sed -n '1,120p' frontend/src/service-worker.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '130,260p' src/main/webapp/WEB-INF/web.xml" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' frontend/src/FrontDesk.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "NO_MATCH_RESULT|NO_MATCH_RESULTS|NO_MATCH_PROSPECTS|MATCH_RESULTS_STILL_PROCESSING|MATCH_RESULT" frontend/src/locale/en.json' in /mnt/c/Wildbook-clean2
 succeeded in 254ms:
/* eslint-disable no-restricted-globals */

// This service worker can be customized!
// See https://developers.google.com/web/tools/workbox/modules
// for the list of available Workbox modules, or add any other
// code you'd like.
// You can also remove this file if you'd prefer not to use a
// service worker, and the Workbox build step will be skipped.

import { clientsClaim } from "workbox-core";
import { ExpirationPlugin } from "workbox-expiration";
import { precacheAndRoute, createHandlerBoundToURL } from "workbox-precaching";
import { registerRoute } from "workbox-routing";
import { StaleWhileRevalidate } from "workbox-strategies";
import { NetworkFirst } from "workbox-strategies";

console.log("Service worker running");

// Skip waiting to activate new service worker immediately
// This ensures users get updates without closing all tabs
self.skipWaiting();
clientsClaim();

// Precache all of the assets generated by your build process.
// Their URLs are injected into the manifest variable below.
// This variable must be present somewhere in your service worker file,
// even if you decide not to use precaching. See https://cra.link/PWA
precacheAndRoute(self.__WB_MANIFEST);

// Set up App Shell-style routing, so that all navigation requests
// are fulfilled with your index.html shell. Learn more at
// https://developers.google.com/web/fundamentals/architecture/app-shell
const fileExtensionRegexp = new RegExp("/[^/?]+\\.[^/]+$");
registerRoute(
  // Return false to exempt requests from being fulfilled by index.html.
  ({ request, url }) => {
    // If this isn't a navigation, skip.
    if (request.mode !== "navigate") {
      return false;
    } // If this is a URL that starts with /_, skip.

    if (url.pathname.startsWith("/_")) {
      return false;
    } // If this looks like a URL for a resource, because it contains // a file extension, skip.

    if (url.pathname.match(fileExtensionRegexp)) {
      return false;
    } // Return true to signal that we want to use the handler.

    return true;
  },
  createHandlerBoundToURL(process.env.PUBLIC_URL + "/index.html"),
);

// An example runtime caching route for requests that aren't handled by the
// precache, in this case same-origin .png requests like those from in public/
registerRoute(
  // Add in any other file extensions or routing criteria as needed.
  ({ url }) =>
    url.origin === self.location.origin && url.pathname.endsWith(".png"), // Customize this strategy as needed, e.g., by changing to CacheFirst.
  new StaleWhileRevalidate({
    cacheName: "images",
    plugins: [
      // Ensure that once this runtime cache reaches a maximum size the
      // least-recently used images are removed.
      new ExpirationPlugin({ maxEntries: 50 }),
    ],
  }),
);

// Never cache API endpoints — responses are not idempotent and stale data
// (e.g. boundingBox, detectionStatus) breaks polling-driven UI updates.
registerRoute(
  ({ url }) => !url.pathname.startsWith("/api/"),
  new NetworkFirst(),
);

// self.addEventListener('fetch', function(event) {
//   event.respondWith(
//     caches.match(event.request).then(function(cachedResponse) {
//       if (cachedResponse) {
//         // Check if the response is expired.
//         const isExpired = isResponseExpired(cachedResponse);
//         // If the response is expired, fetch and update the cache.
//         if (isExpired) {
//           return fetchAndUpdateCache(event.request);
//         } else {
//           // If the response is not expired, return it from the cache.
//           return cachedResponse;
//         }
//       } else {
//         // If the request is not in the cache, fetch and update the cache.
//         return fetchAndUpdateCache(event.request);
//       }
//     })
//   );
// });
// // This function checks if the response is expired.
// function isResponseExpired(response) {
//   // Check if the Cache-Control header is set to no-cache.
//   if (!response.headers.has('Cache-Control') && !response.headers.has('Expires')) {
//     return true;
//   }

//   // Check if the Cache-Control header is set to no-cache or no-store.
//   const cacheControl = response.headers.get('Cache-Control');
//   // If the Cache-Control header is set to no-cache or no-store, return true.
//   if (cacheControl) {
//     if (/(no-cache|no-store)/.test(cacheControl)) {
//       return true;
//     }
//     // Check if the Cache-Control header is set to max-age.
//     const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
//     // If the Cache-Control header is set to max-age, check if the response is expired.
//     // If the response is expired, return true.
//     if (maxAgeMatch) {
//       const maxAge = parseInt(maxAgeMatch[1], 10);
//       const dateHeader = response.headers.get('Date');
//       const age = dateHeader ? (Date.now() - new Date(dateHeader).getTime()) / 1000 : 0;
//       return age > maxAge;

 succeeded in 256ms:
				/FixStandardImport = authc, roles[admin]
				/ImportTaskDetection = authc, roles[admin]
				/imports.jsp = authc
				/DeleteImportTask = authc, roles[researcher]
				/BatchUpload/** = authc, roles[admin]

				# ===== Export Operations =====
				/GenePopExport = authc, roles[researcher]
				/SOCPROGExport = authc, roles[researcher]
				/EncounterSearchExportGeneGISFormat = authc, roles[researcher]
				/EncounterSearchExportKML = authc, roles[researcher]
				/EncounterSearchExportExcelFile = authc, roles[researcher]
				/EncounterAnnotationExportExcelFile = authc, roles[researcher]
				/EncounterSearchExportMetadataExcel = authc, roles[researcher]
				/EncounterSearchExportShapefile = authc, roles[researcher]
				/EncounterSearchExportEmailAddresses = authc, roles[researcher]
				/OccurrenceSearchExportMetadataExcel = authc, roles[researcher]
				/OccurrenceSearchExportGtm = authc, roles[researcher]
				/SimpleCMROutput = authc, roles[researcher]
				/MarkRecaptureEncounters = authc, roles[researcher]
				/GenalexExportCodominantMSDataBySize = authc, roles[researcher]
				/IndividualSearchExportCapture = authc, roles[researcher]
				/CRCExportReport = authc, roles[researcher]
				/KinalyzerExport = authc, roles[researcher]
				/ExportWekaPredictorARFF = authc, roles[admin]
				/occurrenceExportSearchResults.jsp = authc, roles[researcher]
				/EncounterSearchExportCOCO = authc, roles[researcher]

				# ===== Search =====
				/encounters/thumbnailSearchResults.jsp = authc, roles[researcher]
				/encounters/encounterSearch.jsp = roles[researcher]
				/encounters/searchResults.jsp = authc, roles[researcher]
				/encounters/searchResultsAnalysis.jsp = authc, roles[researcher]
				/encounters/mappedSearchResults.jsp = authc, roles[researcher]
				/encounters/mappedSearchResultsSex.jsp = authc, roles[researcher]
				/encounters/mappedSearchResultsHaplotype.jsp = authc, roles[researcher]
				/encounters/exportSearchResults.jsp = authc, roles[researcher]
				/encounters/imageViewer.jsp = authc, roles[researcher]
				/encounters/searchComparison.jsp = authc, roles[researcher]
				/encounters/searchComparisonResults.jsp = authc, roles[researcher]
				/individualSearch.jsp = authc, roles[researcher]
				/individualSearchResults.jsp = authc
				/individualSearchResultsExport.jsp = authc, roles[researcher]
				/individualSearchResultsAnalysis.jsp = authc, roles[researcher]
				/individualMappedSearchResults.jsp = authc, roles[researcher]
				/individualThumbnailSearchResults.jsp = authc, roles[researcher]
				/occurrenceSearch.jsp = authc, roles[researcher]
				/occurrence.jsp = authc, roles[researcher]
				/occurrenceSearchResults.jsp = authc, roles[researcher]

				# ===== Individual Management =====
				/IndividualSetNickName = authc, roles[researcher]
				/IndividualSetName = authc, roles[researcher]
				/IndividualSetAlternateID = authc, roles[researcher]
				/IndividualRemoveEncounter = authc, roles[researcher]
				/IndividualAddEncounter = authc, roles[researcher]
				/IndividualAddFile = authc, roles[researcher]
				/IndividualRemoveDataFile = authc, roles[researcher]
				/IndividualAddComment = authc, roles[researcher]
				/IndividualSetSex = authc, roles[researcher]
				/IndividualSetYearOfBirth = authc, roles[researcher]
				/IndividualSetYearOfDeath = authc, roles[researcher]
				/IndividualCreateForProject = authc, roles[researcher]
				/IndividualAddIncrementalProjectId = authc, roles[researcher]
				/MergeIndividual = authc, roles[researcher]
				/ScheduledIndividualMergeUpdate = authc, roles[researcher]
				/GetIndividualSearchGoogleMapsPoints = authc, roles[researcher]
				/merge.jsp = authc, roles[researcher]
				/mergeComplete.jsp = authc, roles[researcher]

				/gallery.jsp = authc
				/pictureBook.jsp = authc, roles[researcher]

				# ===== Encounter Management =====
				/EncounterSetAlternateID = authc, roles[researcher]
				/EncounterSetState = authc, roles[researcher]
				/EncounterSetScarring = authc, roles[researcher]
				/EncounterSetGPS = authc, roles[researcher]
				/EncounterSetVerbatimEventDate = authc, roles[researcher]
				/EncounterSetLifeStage = authc, roles[researcher]
				/EncounterSetCountry = authc, roles[researcher]
				/EncounterSetTissueSample = authc, roles[researcher]
				/EncounterSetSubmitterID = authc, roles[researcher]
				/EncounterSetTapirLinkExposure = authc, roles[researcher]
				/EncounterRemoveTissueSample = authc, roles[researcher]
				/EncounterRemoveImage = authc, roles[researcher]
				/EncounterSetGenusSpecies = authc, roles[researcher]
				#/EncounterSetInformOthers = authc, roles[researcher]
				/EncounterResetDate = authc, roles[researcher]
				/EncounterSetLocation = authc, roles[researcher]
				/EncounterSetLocationID = authc, roles[researcher]
				/EncounterSetSex = authc, roles[researcher]
				/EncounterSetMaximumDepth = authc, roles[researcher]
				/EncounterSetMaximumElevation = authc, roles[researcher]
				/EncounterSetOccurrenceRemarks = authc, roles[researcher]
				/EncounterSetPatterningCode = authc, roles[researcher]
				/EncounterSetReleaseDate = authc, roles[researcher]
				/EncounterSetMeasurements = authc, roles[researcher]
				/EncounterSetTags = authc, roles[researcher]
				/EncounterSetDynamicProperty = authc, roles[researcher]
				/EncounterSetMatchedBy = authc, roles[researcher]
				/EncounterSetLivingStatus = authc, roles[researcher]
				/EncounterSetSurveyAndTrack = authc, roles[researcher]
				/EncounterDelete = authc, roles[researcher]
				/EncounterSetObservation = authc, roles[researcher]
				/EncounterVMData = authc, roles[researcher]
				/EncounterRemoveUser = authc, roles[researcher]
				/EncounterAddUser = authc, roles[researcher]
				/EncounterSetString = authc
				/EncounterRemoveAnnotation = authc, roles[researcher]
				/GetEncounterSearchGoogleMapsPoints = authc, roles[researcher]
				/RemoveDynamicProperty = authc, roles[researcher]
				/ResurrectDeletedEncounter = authc, roles[admin]

				#/encounters/encounter.jsp = authc
				/encounters/encounterMediaGallery.jsp = authc
				/encounters/projectManagement.jsp = authc
				/encounters/encounterSpotTool.jsp = authc, roles[imageProcessor]
				/encounters/encounterSpotVisualizer.jsp = authc, roles[researcher]
				/encounters/manualAnnotation.jsp = authc, roles[researcher]
				/encounters/encounterVM.jsp = authc, roles[researcher]

				# ===== Spots =====
				/SubmitSpots = authc, roles[imageProcessor]
				/InterconnectSubmitSpots = authc, roles[imageProcessor]
				/interconnectSubmitSpots = authc, roles[imageProcessor]
				/SubmitSpotsAndImage = authc, roles[imageProcessor]
				/SubmitSpotsAndTransformImage = authc,roles[researcher]
				/EncounterAddSpotFile = authc, roles[identifier]
				/EncounterRemoveSpots = authc, roles[researcher]
				/GrothMatch = authc, roles[researcher]

 succeeded in 290ms:
import React, { useEffect, useState } from "react";
import UnauthenticatedSwitch from "./UnAuthenticatedSwitch";
import AuthenticatedSwitch from "./AuthenticatedSwitch";
import axios from "axios";
import AuthContext from "./AuthProvider";
import getMergeNotifications from "./models/notifications/getMergeNotifications";
import getCollaborationNotifications from "./models/notifications/getCollaborationNotifications";
import LoadingScreen from "./components/LoadingScreen";
import GoogleTagManager from "./GoogleTagManager";
import "./css/scrollBar.css";
import SessionWarning from "./components/SessionWarning";
import {
  sessionWarningTime,
  sessionCountdownTime,
} from "./constants/sessionWarning";
import useDocumentTitle from "./hooks/useDocumentTitle";
import { useSiteSettings } from "./SiteSettingsContext";

export default function FrontDesk() {
  useDocumentTitle();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [collaborationTitle, setCollaborationTitle] = useState();
  const [collaborationData, setCollaborationData] = useState([]);
  const [mergeData, setMergeData] = useState([]);
  const [count, setCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const { data, isLoading: siteSettingsLoading } = useSiteSettings();
  const showclassicsubmit = data?.showClassicSubmit;
  const showClassicEncounterSearch = data?.showClassicEncounters;
  const showHowToPhotograph = data?.showHowToPhotograph;
  const checkLoginStatus = () => {
    axios
      .head("/api/v3/user")
      .then((response) => {
        if (response.status === 200) {
          setIsLoggedIn(true);
        }
        setLoading(false);
      })
      .catch((error) => {
        if (error.response?.status === 401) {
          setIsLoggedIn(false);
          setLoading(false);
        } else {
          console.warn("Login status check failed (non-401):", error.message);
        }
      });
  };

  const getAllNotifications = async () => {
    const { collaborationTitle, collaborationData } =
      await getCollaborationNotifications();
    const mergeData = await getMergeNotifications();
    const count = collaborationData.length + mergeData.length;
    setCollaborationTitle(collaborationTitle);
    setCollaborationData(collaborationData);
    setMergeData(mergeData);
    setCount(count);
  };

  useEffect(() => {
    checkLoginStatus();
    const intervalId = setInterval(() => {
      checkLoginStatus();
    }, 60000);

    return () => clearInterval(intervalId);
  }, []);

  useEffect(() => {
    const handleOnline = () => {
      checkLoginStatus();
    };
    window.addEventListener("online", handleOnline);
    return () => window.removeEventListener("online", handleOnline);
  }, []);

  useEffect(() => {
    if (isLoggedIn) {
      getAllNotifications();
    }
  }, [isLoggedIn]);

  if (loading) return <LoadingScreen />;

  if (isLoggedIn) {
    return (
      <AuthContext.Provider
        value={{
          isLoggedIn,
          setIsLoggedIn,
          count,
          collaborationTitle,
          collaborationData,
          mergeData,
          getAllNotifications,
        }}
      >
        <GoogleTagManager />
        <SessionWarning
          sessionWarningTime={sessionWarningTime}
          sessionCountdownTime={sessionCountdownTime}
        />
        <AuthenticatedSwitch
          showclassicsubmit={showclassicsubmit}
          showClassicEncounterSearch={showClassicEncounterSearch}
          showHowToPhotograph={showHowToPhotograph}
          siteSettingsLoading={siteSettingsLoading}
        />
      </AuthContext.Provider>
    );
  }

  return (
    <AuthContext.Provider value={{ isLoggedIn }}>
      <GoogleTagManager />
      <UnauthenticatedSwitch
        showclassicsubmit={showclassicsubmit}
        showHowToPhotograph={showHowToPhotograph}
        siteSettingsLoading={siteSettingsLoading}

 succeeded in 316ms:
594:    "MATCH_RESULTS": "Match Results",
705:    "CLICK_ANNOTATION_TO_SEE_MATCH_RESULTS": "Click on an annotation to view match results",
762:    "MATCH_RESULT": "Match Result",
822:    "MATCH_RESULTS_STILL_PROCESSING": "Match results are still processing.",
828:    "NO_MATCH_RESULT": "Image Analysis has returned and no match was found.",
829:    "NO_MATCH_RESULTS_AVAILABLE": "No match results available yet. The job may still be processing, or there may be no data. Please refresh the page to check again.",
831:    "MATCH_RESULTS_PROCESSING_FAILED": "Match results processing failed.",
832:    "MATCH_RESULTS_ERROR_REQUIRED": "Missing or insufficient data",
833:    "MATCH_RESULTS_ERROR_INVALID": "Invalid results",
834:    "MATCH_RESULTS_ERROR_UNKNOWN": "Unknown error",
835:    "MATCH_RESULTS_ERROR_TIMEOUT": "Task has timed out",
837:    "NO_MATCH_PROSPECTS": "We couldn't find any matches based on your current filters. Please try adjusting them or consider this a new individual."

exec
/bin/bash -lc "sed -n '230,430p' frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,160p' frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '120,360p' frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,120p' frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js" in /mnt/c/Wildbook-clean2
 succeeded in 227ms:
import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { IntlProvider } from "react-intl";
import axios from "axios";
import MatchResults from "../../../pages/MatchResultsPage/MatchResults";

jest.mock("axios");

jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: () => ({
    projectsForUser: {
      "proj-1": { name: "Project Alpha", prefix: "PA" },
      "proj-2": { name: "Project Beta", prefix: "PB" },
    },
    identificationRemarks: ["Confirmed", "Uncertain"],
  }),
}));

jest.mock("../../../components/FullScreenLoader", () => {
  const React = require("react");
  function FullScreenLoader() {
    return React.createElement("div", { "data-testid": "full-screen-loader" });
  }
  FullScreenLoader.displayName = "FullScreenLoader";
  return FullScreenLoader;
});

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchProspectTable",
  () => {
    const React = require("react");
    function MatchProspectTable({ taskId, columns, onToggleSelected }) {
      return React.createElement(
        "div",
        { "data-testid": "prospect-table-" + taskId },
        (columns || []).flat().map(function (col, i) {
          return React.createElement(
            "button",
            {
              key: i,
              "data-testid": "prospect-row",
              onClick: function () {
                if (onToggleSelected) {
                  onToggleSelected(
                    true,
                    "key-" + i,
                    "enc-" + i,
                    "ind-" + i,
                    "Name" + i,
                  );
                }
              },
            },
            "row-" + i,
          );
        }),
      );
    }
    MatchProspectTable.displayName = "MatchProspectTable";
    return MatchProspectTable;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchResultsBottomBar",
  () => {
    const React = require("react");
    function MatchResultsBottomBar() {
      return React.createElement("div", { "data-testid": "bottom-bar" });
    }
    MatchResultsBottomBar.displayName = "MatchResultsBottomBar";
    return MatchResultsBottomBar;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/InstructionsModal",
  () => {
    const React = require("react");
    function InstructionsModal({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "instructions-modal" },
        React.createElement(
          "button",
          { onClick: onHide, "data-testid": "close-instructions" },
          "Close",
        ),
      );
    }
    InstructionsModal.displayName = "InstructionsModal";
    return InstructionsModal;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchCriteriaDrawer",
  () => {
    const React = require("react");
    function MatchCriteriaDrawer({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "match-criteria-drawer" },
        React.createElement(
          "button",
          { onClick: onHide, "data-testid": "close-drawer" },
          "Close",
        ),
      );
    }
    MatchCriteriaDrawer.displayName = "MatchCriteriaDrawer";
    return MatchCriteriaDrawer;
  },
);

jest.mock("../../../components/MultiSelectWithCheckbox", () => {
  const React = require("react");
  function MultiSelectWithCheckbox({
    options,
    value,
    onChangeCommitted,
    placeholder,
  }) {
    return React.createElement(
      "select",
      {
        "data-testid": "project-multiselect",
        value: value[0] || "",
        onChange: function (e) {
          onChangeCommitted([e.target.value]);
        },
      },
      React.createElement("option", { value: "" }, placeholder),
      (options || []).map(function (o) {
        return React.createElement(
          "option",
          { key: o.value, value: o.value },
          o.label,
        );
      }),
    );
  }
  MultiSelectWithCheckbox.displayName = "MultiSelectWithCheckbox";
  return MultiSelectWithCheckbox;
});

// ---------------------------------------------------------------------------

const makeApiResponse = () => ({
  matchResultsRoot: {

 succeeded in 234ms:
  });

  test("renders bottom bar when results are available", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    expect(await screen.findByTestId("bottom-bar")).toBeInTheDocument();
  });

  test("renders INDIVIDUAL_SCORE and IMAGE_SCORE view mode buttons", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByText("INDIVIDUAL_SCORE")).toBeInTheDocument();
    expect(screen.getByText("IMAGE_SCORE")).toBeInTheDocument();
  });

  test("clicking IMAGE_SCORE button does not crash", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    await act(async () => {
      fireEvent.click(screen.getByText("IMAGE_SCORE"));
    });
    expect(screen.getByText("IMAGE_SCORE")).toBeInTheDocument();
  });

  test("InfoIcon click opens InstructionsModal", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.queryByTestId("instructions-modal")).not.toBeInTheDocument();
    const infoWrapper = screen.getByTitle("Match Page Instructions");
    fireEvent.click(within(infoWrapper).getByRole("button"));
    expect(screen.getByTestId("instructions-modal")).toBeInTheDocument();
  });

  test("Closing InstructionsModal hides it", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const infoWrapper = screen.getByTitle("Match Page Instructions");
    fireEvent.click(within(infoWrapper).getByRole("button"));
    fireEvent.click(screen.getByTestId("close-instructions"));
    expect(screen.queryByTestId("instructions-modal")).not.toBeInTheDocument();
  });

  test("FilterIcon click opens MatchCriteriaDrawer", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(
      screen.queryByTestId("match-criteria-drawer"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle("Match Criteria"));
    expect(screen.getByTestId("match-criteria-drawer")).toBeInTheDocument();
  });

  test("Closing MatchCriteriaDrawer hides it", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    fireEvent.click(screen.getByTitle("Match Criteria"));
    fireEvent.click(screen.getByTestId("close-drawer"));
    expect(
      screen.queryByTestId("match-criteria-drawer"),
    ).not.toBeInTheDocument();
  });

  test("NUMBER_OF_RESULTS label is rendered", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByText("NUMBER_OF_RESULTS")).toBeInTheDocument();
  });

  test("numResults input accepts numeric value", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.change(input, { target: { value: "20" } });
    expect(input.value).toBe("20");
  });

  test("non-numeric input is rejected in numResults field", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.change(input, { target: { value: "abc" } });
    expect(input.value).toBe("12");
  });

  test("pressing Enter on numResults input triggers fetch", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(axios.get).toHaveBeenCalled());
  });

  test("focus on numResults input shows confirm checkmark button", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    fireEvent.focus(screen.getByDisplayValue("12"));
    expect(screen.getByTitle("Apply changes")).toBeInTheDocument();
  });

  test("blur on numResults input hides confirm checkmark button", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.focus(input);
    fireEvent.blur(input);
    expect(screen.queryByTitle("Apply changes")).not.toBeInTheDocument();
  });

  test("renders PROJECT label and MultiSelectWithCheckbox", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByTestId("project-multiselect")).toBeInTheDocument();
  });

  test("selecting a project triggers re-fetch", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    await act(async () => {
      fireEvent.change(screen.getByTestId("project-multiselect"), {
        target: { value: "proj-1" },
      });
    });
    await waitFor(() => expect(axios.get).toHaveBeenCalled());
  });

  test("shows 'no match results' message when API returns empty prospects", async () => {
    axios.get.mockResolvedValueOnce({
      data: {
        matchResultsRoot: {
          id: "task-1",
          method: { name: "hs" },
          matchResults: {
            numberCandidates: 0,
            queryAnnotation: {},
            prospects: { annot: [], indiv: [] },
          },
          children: [],
        },
      },
    });
    renderComponent();
    expect(await screen.findByText(/NO_MATCH_RESULT/i)).toBeInTheDocument();
  });

  test("does not crash when API call fails", async () => {
    axios.get.mockRejectedValueOnce(new Error("network error"));
    renderComponent();
    expect(await screen.findByText(/NO_MATCH_RESULT/i)).toBeInTheDocument();
  });
});

 succeeded in 254ms:
    store.setHasResults(true);
    expect(store.hasResults).toBe(true);
    store.setHasResults(false);
    expect(store.hasResults).toBe(false);
  });

  test("setNewIndividualName updates newIndividualName", () => {
    store.setNewIndividualName("Nemo");
    expect(store.newIndividualName).toBe("Nemo");
  });

  test("setProjectNames updates projectNames and triggers fetch when taskId set", async () => {
    store.setTaskId("t-1");
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    store.setProjectNames(["proj-1", "proj-2"]);
    expect(store.projectNames).toEqual(["proj-1", "proj-2"]);
  });

  test("setProjectNames normalises non-array to empty array", () => {
    store.setProjectNames(null, { fetch: false });
    expect(store.projectNames).toEqual([]);
  });

  test("setProjectNames is no-op when value unchanged", () => {
    store._projectNames = ["p1"];
    store.setTaskId("t-1");
    axios.get.mockClear();
    store.setProjectNames(["p1"]);
    // value unchanged — fetch should not be triggered
    expect(axios.get).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — loadData", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("returns task info with hasResults=false when prospects are empty but task is terminal", () => {
    store.loadData({
      matchResultsRoot: makeProspect({
        statusOverall: "completed", // Terminal status
        matchResults: {
          numberCandidates: 0,
          queryAnnotation: {},
          prospects: { annot: [], indiv: [] },
        },
      }),
    });
    // Even with empty prospects, we get task info to show "no results" in UI
    expect(store.hasResults).toBe(true);
    expect(store._rawAnnots.length).toBe(1);
    expect(store._rawIndivs.length).toBe(1);
    // But the items should have hasResults: false
    expect(store._rawAnnots[0].hasResults).toBe(false);
    expect(store._rawIndivs[0].hasResults).toBe(false);
  });

  test("clears state when matchResultsRoot is null", () => {
    store.loadData({ matchResultsRoot: null });
    expect(store.hasResults).toBe(false);
  });

  test("sets hasResults to true and populates raw arrays on valid data", () => {
    store.loadData(makeApiResponse());
    expect(store.hasResults).toBe(true);
    expect(store._rawIndivs.length).toBeGreaterThan(0);
    expect(store._rawAnnots.length).toBeGreaterThan(0);
  });

  test("sets encounterId from queryAnnotation.encounter.id", () => {
    store.loadData(makeApiResponse());
    expect(store.encounterId).toBe("enc-query");
  });

  test("sets individualId from queryAnnotation.individual.id", () => {
    store.loadData(makeApiResponse());
    expect(store.individualId).toBe("ind-query");
  });

  test("sets individualDisplayName", () => {
    store.loadData(makeApiResponse());
    expect(store.individualDisplayName).toBe("Luna");
  });

  test("sets queryImageUrl in processed data metadata", () => {
    store.loadData(makeApiResponse());
    const sections = store.processedIndivs;
    expect(sections.length).toBeGreaterThan(0);
    expect(sections[0].metadata.queryImageUrl).toBe(
      "http://img.test/query.jpg",
    );
  });

  test("clears selectedMatch after loading (resetSelectionToQuery called)", () => {
    store._selectedMatch = [{ key: "k1", encounterId: "e1" }];
    store.loadData(makeApiResponse());
    expect(store.selectedMatch).toEqual([]);
  });

  test("uses annot first item when viewMode is image", () => {
    store.setViewMode("image");
    store.loadData(makeApiResponse());
    expect(store.hasResults).toBe(true);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — _processData", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("returns empty array for empty input", () => {
    expect(store._processData([])).toEqual([]);
  });

  test("groups items by taskId into sections", () => {
    const items = [
      {
        taskId: "t1",
        score: 0.9,
        numberCandidates: 5,
        date: "2024-01-01",
        methodName: "hs",
        methodDescription: "HotSpotter",
        queryEncounterImageAsset: null,
        queryEncounterImageUrl: null,
        queryEncounterAnnotation: {},
        taskStatus: "done",
        taskStatusOverall: "done",
        algorithm: "hs",
      },
      {
        taskId: "t1",
        score: 0.8,
        numberCandidates: 5,
        date: "2024-01-01",
        methodName: "hs",
        methodDescription: "HotSpotter",
        queryEncounterImageAsset: null,
        queryEncounterImageUrl: null,
        queryEncounterAnnotation: {},
        taskStatus: "done",
        taskStatusOverall: "done",
        algorithm: "hs",
      },
      {
        taskId: "t2",
        score: 0.7,
        numberCandidates: 3,
        date: "2024-01-02",
        methodName: "fin",
        methodDescription: "Finprint",
        queryEncounterImageAsset: null,
        queryEncounterImageUrl: null,
        queryEncounterAnnotation: {},
        taskStatus: "done",
        taskStatusOverall: "done",
        algorithm: "fin",
      },
    ];
    const sections = store._processData(items);
    expect(sections).toHaveLength(2);
    const t1 = sections.find((s) => s.taskId === "t1");
    expect(t1).toBeDefined();
    expect(t1.columns.flat()).toHaveLength(2);
  });

  test("splits items into columns of MAX_ROWS_PER_COLUMN (4)", () => {
    const items = Array.from({ length: 9 }, (_, i) => ({
      taskId: "t1",
      score: i * 0.1,
      numberCandidates: 1,
      date: "2024-01-01",
      methodName: "m",
      methodDescription: "d",
      queryEncounterImageAsset: null,
      queryEncounterImageUrl: null,
      queryEncounterAnnotation: {},
      taskStatus: "done",
      taskStatusOverall: "done",
      algorithm: "m",
    }));
    const sections = store._processData(items);
    expect(sections).toHaveLength(1);
    const { columns } = sections[0];
    // 9 items → ceil(9/4) = 3 columns
    expect(columns).toHaveLength(3);
    expect(columns[0]).toHaveLength(4);
    expect(columns[1]).toHaveLength(4);
    expect(columns[2]).toHaveLength(1);
  });

  test("attaches displayIndex starting from 1", () => {
    const items = Array.from({ length: 3 }, (_, i) => ({
      taskId: "t1",
      score: i,
      numberCandidates: 0,
      date: "d",
      methodName: "m",
      methodDescription: "d",
      queryEncounterImageAsset: null,
      queryEncounterImageUrl: null,
      queryEncounterAnnotation: {},
      taskStatus: null,
      taskStatusOverall: null,
      algorithm: "m",
    }));
    const sections = store._processData(items);
    const flat = sections[0].columns.flat();
    expect(flat.map((f) => f.displayIndex)).toEqual([1, 2, 3]);
  });

  test("section metadata picks values from first item", () => {
    const items = [
      {
        taskId: "t1",
        numberCandidates: 42,
        date: "2024-07-04",
        methodName: "mymeth",
        methodDescription: "desc",
        queryEncounterImageAsset: { url: "http://asset.test/img.jpg" },
        queryEncounterImageUrl: "http://asset.test/img.jpg",
        queryEncounterAnnotation: { x: 1 },
        taskStatus: "running",
        taskStatusOverall: "running",
        algorithm: "mymeth",
      },
    ];
    const sections = store._processData(items);
    const meta = sections[0].metadata;
    expect(meta.numCandidates).toBe(42);
    expect(meta.date).toBe("2024-07-04");
    expect(meta.methodName).toBe("mymeth");
    expect(meta.algorithm).toBe("mymeth");

 succeeded in 258ms:
import MatchResultsStore from "../../../pages/MatchResultsPage/stores/matchResultsStore";
import axios from "axios";

jest.mock("axios");

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const makeProspect = (overrides = {}) => ({
  id: "task-1",
  status: "complete",
  statusOverall: "complete",
  dateCreated: "2024-06-01",
  method: { name: "hotspotter", description: "HotSpotter" },
  matchingSetFilter: {},
  matchResults: {
    numberCandidates: 10,
    queryAnnotation: {
      x: 0.1,
      y: 0.2,
      width: 0.3,
      height: 0.4,
      theta: 0,
      asset: { url: "http://img.test/query.jpg" },
      encounter: { id: "enc-query", locationId: "loc-1" },
      individual: { id: "ind-query", displayName: "Luna" },
    },
    prospects: {
      annot: [{ annotId: "a1", score: 0.9 }],
      indiv: [{ individualId: "i1", score: 0.85 }],
    },
  },
  children: [],
  ...overrides,
});

const makeApiResponse = (nodeOverrides = {}) => ({
  matchResultsRoot: makeProspect(nodeOverrides),
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — initial state", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("viewMode defaults to 'individual'", () => {
    expect(store.viewMode).toBe("individual");
  });

  test("numResults defaults to 12", () => {
    expect(store.numResults).toBe(12);
  });

  test("projectNames defaults to empty array", () => {
    expect(store.projectNames).toEqual([]);
  });

  test("selectedMatch defaults to empty array", () => {
    expect(store.selectedMatch).toEqual([]);
  });

  test("loading defaults to false", () => {
    expect(store.loading).toBe(false);
  });

  test("hasResults defaults to false", () => {
    expect(store.hasResults).toBe(false);
  });

  test("taskId defaults to null", () => {
    expect(store.taskId).toBeNull();
  });

  test("matchRequestError defaults to null", () => {
    expect(store.matchRequestError).toBeNull();
  });

  test("newIndividualName defaults to empty string", () => {
    expect(store.newIndividualName).toBe("");
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — setters", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("setViewMode updates viewMode", () => {
    store.setViewMode("image");
    expect(store.viewMode).toBe("image");
    store.setViewMode("individual");
    expect(store.viewMode).toBe("individual");
  });

  test("setTaskId updates taskId", () => {
    store.setTaskId("abc-123");
    expect(store.taskId).toBe("abc-123");
  });

  test("setNumResults updates numResults", () => {
    store.setNumResults(25);
    expect(store.numResults).toBe(25);
  });

  test("setLoading toggles loading flag", () => {
    store.setLoading(true);
    expect(store.loading).toBe(true);
    store.setLoading(false);
    expect(store.loading).toBe(false);
  });

  test("setHasResults updates hasResults", () => {
    store.setHasResults(true);

exec
/bin/bash -lc 'rg -n "MatchResultProspect|MATCHRESULTPROSPECT|scoreType|score_type|SCORETYPE|prospects" src/main/resources src/main/webapp/WEB-INF src/test frontend/src/__tests__ | head -200' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git branch --show-current && git log --oneline --decorate -n 25' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"getMatchResults\\(|MatchResult\" src/main/java/org/ecocean/shepherd src/main/java/org/ecocean -g'*.java'" in /mnt/c/Wildbook-clean2
 succeeded in 1340ms:
src/test/java/org/ecocean/MatchResultTest.java:9:import org.ecocean.ia.MatchResultProspect;
src/test/java/org/ecocean/MatchResultTest.java:67:        JSONObject pj = mr.prospectsForApiGet(-1, null, myShepherd);
src/test/java/org/ecocean/MatchResultTest.java:99:    @Test void basicMatchResultProspect() {
src/test/java/org/ecocean/MatchResultTest.java:100:        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);
src/test/java/org/ecocean/OpenSearchAnnotationMatchableQueryTest.java:21: * <p>(Empty-match-prospects design Track 2 C8.)</p>
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:10: * accessor needed by the empty-match-prospects Track 2 batch gate so a
src/test/java/org/ecocean/ia/TaskGetStoredStatusTest.java:13: * performs. (Empty-match-prospects design Track 2 C7.)
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:23: * <p>(Empty-match-prospects design Track 2 C10.)</p>
src/test/java/org/ecocean/ia/MatchResultClampBboxTest.java:13: * rejections and the shared-tmpArr bug. (Empty-match-prospects
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:19: * prospects design Track 2 C13.)
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:90:        // prospects field is null by default
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:100:        MatchResultProspect p1 = new MatchResultProspect(a1, 1.0d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:101:        MatchResultProspect p2 = new MatchResultProspect(a2, 0.5d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:104:        // adding via reflection on the prospects field.
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:105:        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:109:        MatchResultProspect found = MatchInspectionPairxEnricher.findProspect(
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:119:        MatchResultProspect annotP = new MatchResultProspect(a1, 1.0d, "annot", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:120:        MatchResultProspect indivP = new MatchResultProspect(a1, 0.8d, "indiv", null);
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:121:        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:125:        MatchResultProspect foundAnnot = MatchInspectionPairxEnricher.findProspect(
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:127:        MatchResultProspect foundIndiv = MatchInspectionPairxEnricher.findProspect(
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:140:        java.util.HashSet<MatchResultProspect> set = new java.util.HashSet<MatchResultProspect>();
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:141:        set.add(new MatchResultProspect(a1, 1.0d, "annot", null));
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:148:        java.util.HashSet<MatchResultProspect> prospects) {
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:150:            java.lang.reflect.Field f = MatchResult.class.getDeclaredField("prospects");
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:152:            f.set(mr, prospects);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:19: * (Empty-match-prospects design Track 2 C9.)
src/test/java/org/ecocean/ia/plugin/WildbookIAMMediaAssetToUriTest.java:16: * leading-NPE tightening. (Empty-match-prospects design Track 1 C2.)
src/test/java/org/ecocean/ia/plugin/WildbookIAMImagePhase0Test.java:24: * (Empty-match-prospects design Track 1 C6.)
src/test/java/org/ecocean/ia/plugin/WildbookIAMImageIdsStrictTest.java:18: * without WireMock/Tomcat. (Empty-match-prospects design Track 1 C3.)
src/test/java/org/ecocean/ia/plugin/WildbookIAMFancyUuidArrayStrictTest.java:22: * report which WBIA response was malformed. (Empty-match-prospects
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:29:    prospects: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:161:  test("returns task info with hasResults=false when prospects are empty but task is terminal", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:168:          prospects: { annot: [], indiv: [] },
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:172:    // Even with empty prospects, we get task info to show "no results" in UI
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:579:          prospects: {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:642:  test("calls correct endpoint with prospectsSize param", async () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:651:      expect.stringContaining("prospectsSize=5"),
src/test/java/org/ecocean/ia/plugin/WbiaRegisterRequestImageFieldsTest.java:14: * to {@code null}. (Empty-match-prospects design Track 1 C5.)
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:26:      prospects: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:44:    test("returns common object when annot prospects is empty but task is running", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:48:          prospects: { annot: [], indiv: [{ individualId: "i1", score: 0.9 }] },
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:58:    test("returns common object when annot prospects is missing but task is running", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:62:          prospects: { indiv: [{ individualId: "i1" }] },
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:72:    test("collects annot prospects and attaches common fields", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:119:          prospects: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:140:    test("handles multiple annot prospects in one node", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:144:          prospects: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:170:    test("returns common object when indiv prospects is empty but task is running", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:174:          prospects: { annot: [{ annotId: "a1" }], indiv: [] },
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:184:    test("collects indiv prospects and attaches common fields", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:201:          prospects: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:212:          prospects: {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:230:          prospects: { annot: [], indiv: [{ individualId: "i1" }] },
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:241:          prospects: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:179:      prospects: {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:369:  test("shows 'no match results' message when API returns empty prospects", async () => {
frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx:378:            prospects: { annot: [], indiv: [] },
src/main/resources/org/ecocean/ia/package.jdo:88:		<field name="prospects" default-fetch-group="false" mapped-by="matchResult">
src/main/resources/org/ecocean/ia/package.jdo:89:			<collection element-type="org.ecocean.ia.MatchResultProspect" dependent-element="true" />
src/main/resources/org/ecocean/ia/package.jdo:102:	<class name="MatchResultProspect">
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:26: * <p>(Empty-match-prospects design Track 2 C11.)</p>

 succeeded in 1465ms:
src/main/java/org/ecocean/Annotation.java:16:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Annotation.java:17:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/Annotation.java:1814:    public long deleteMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1815:        return myShepherd.deleteMatchResults(this);
src/main/java/org/ecocean/Annotation.java:1818:    // similar as above for MatchResultProspects
src/main/java/org/ecocean/Annotation.java:1819:    public int deleteMatchResultProspects(Shepherd myShepherd) {
src/main/java/org/ecocean/Annotation.java:1820:        List<MatchResultProspect> mrps = myShepherd.getMatchResultProspects(this);
src/main/java/org/ecocean/Annotation.java:1823:        for (MatchResultProspect mrp : mrps) {
src/main/java/org/ecocean/Annotation.java:1825:            System.out.println("[DEBUG] (" + ct + ") ann.deleteMatchResultProspects() on id=" +
src/main/java/org/ecocean/Annotation.java:1853:        long nm = this.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/Annotation.java:1854:        int np = this.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/Annotation.java:1857:            " MatchResults, " + np + " MatchResultProspects, " + ne + " Embeddings on " + this);
src/main/java/org/ecocean/identity/IBEISIA.java:1884:                MatchResult mr = new MatchResult(task, j, myShepherd);
src/main/java/org/ecocean/identity/IBEISIA.java:1889:                System.out.println("processCallbackIdentify() failed to create MatchResult on " +
src/main/java/org/ecocean/identity/IBEISIA.java:1893:                    "Creation of MatchResult upon task completion failed due to: " + ex);
src/main/java/org/ecocean/Encounter.java:2526:        // NOTE: opposite logic in MatchResultProspect.isInProject()
src/main/java/org/ecocean/shepherd/core/Shepherd.java:19:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:20:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2809:        MatchResult mr = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2812:            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2821:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2824:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2829:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2835:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2838:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2843:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2848:    // faster deletion of all MatchResults associated with Annotation
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2849:    public long deleteMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2852:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2857:        System.out.println("[DEBUG] deleteMatchResults() deleted " + ct + " [" + (System.currentTimeMillis() - t) + "ms] on " + ann);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2861:    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2862:        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2865:        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2869:        if (c != null) all = new ArrayList<MatchResultProspect>(c);
src/main/java/org/ecocean/Embedding.java:18:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/Embedding.java:377:                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
src/main/java/org/ecocean/Embedding.java:381:                System.out.println("findMatchProspects() MatchResult creation failed on " +
src/main/java/org/ecocean/Embedding.java:392:                    "MatchResult persistence failed");
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:21: * {@link MatchResult}'s prospects with PairX inspection MediaAssets.
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:22: * Replaces the previous in-{@link MatchResult}-constructor PairX calls
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:28: *       {@link #loadDtos(String)} loads the MatchResult, walks its
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:42: * same MatchResult still get processed.</p>
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:55:     * For each prospect of the named MatchResult that lacks an
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:62:    public int enrichMatchResult(String matchResultId) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:99:     * Phase A: load all PairxDtos for the given MatchResult under one
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:108:            MatchResult mr = shep.getMatchResult(matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:122:            int[] queryBbox = MatchResult.clampBbox(queryAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:125:                for (MatchResultProspect prospect : mr.getProspects()) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:128:                    // same MatchResult under operator-driven re-fire).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:133:                    int[] prospectBbox = MatchResult.clampBbox(pAnn.getBbox());
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:169:        if (MatchResult.isDegenerateBbox(dto.queryBbox) ||
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:170:            MatchResult.isDegenerateBbox(dto.prospectBbox)) {
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:177:        URL pairxUrl = MatchResult._getPairxUrl(dto.taxonomyString);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:192:     * {@code MatchResult.createInspectionPairxAsset} sent, with the
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:207:        MatchResult.addBboxPayload(payload, dto.queryBbox, dto.prospectBbox);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:214:     * within the MatchResult. Returns true on successful attach, false
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:223:            MatchResult mr = shep.getMatchResult(dto.matchResultId);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:228:            MatchResultProspect target = findProspect(mr, dto.prospectAnnotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:261:     * Find a prospect in the given MatchResult by (annotationId, scoreType).
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:264:    static MatchResultProspect findProspect(MatchResult mr, String annotationId,
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:267:        for (MatchResultProspect p : mr.getProspects()) {
src/main/java/org/ecocean/ia/Task.java:777:    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:778:        return myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:781:    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:782:        List<MatchResult> all = myShepherd.getMatchResults(this);
src/main/java/org/ecocean/ia/Task.java:789:    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
src/main/java/org/ecocean/ia/Task.java:790:        List<MatchResult> mrs = new ArrayList<MatchResult>();
src/main/java/org/ecocean/ia/Task.java:800:                    MatchResult mr = new MatchResult(log, myShepherd);
src/main/java/org/ecocean/ia/Task.java:801:                    System.out.println("[INFO] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:808:                    System.out.println("[ERROR] generateMatchResults() [log t=" +
src/main/java/org/ecocean/ia/Task.java:812:                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
src/main/java/org/ecocean/ia/Task.java:828:        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
src/main/java/org/ecocean/ia/Task.java:844:            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
src/main/java/org/ecocean/ia/Task.java:847:            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
src/main/java/org/ecocean/ia/Task.java:848:               as vector results should have generated their MatchResult upon completion
src/main/java/org/ecocean/ia/Task.java:850:            MatchResult mr = getLatestMatchResult(myShepherd);
src/main/java/org/ecocean/ia/Task.java:853:                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
src/main/java/org/ecocean/ia/Task.java:855:                List<MatchResult> mrs = generateMatchResults(myShepherd);
src/main/java/org/ecocean/ia/Task.java:856:                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
src/main/java/org/ecocean/api/GenericObject.java:112:                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
src/main/java/org/ecocean/api/GenericObject.java:130:                            // this means we created on-the-fly some MatchResult(s) that need persisting
src/main/java/org/ecocean/ia/MlServiceProcessor.java:549:            // MatchResult + prospects are already persisted with
src/main/java/org/ecocean/ia/MlServiceProcessor.java:566:     * MatchResult attached to a child of {@code matchTaskId}. Runs
src/main/java/org/ecocean/ia/MlServiceProcessor.java:573:        List<String> mrIds = collectMatchResultIds(matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:579:                enricher.enrichMatchResult(mrId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:589:     * Open a short Shepherd, list MatchResult IDs attached to children
src/main/java/org/ecocean/ia/MlServiceProcessor.java:593:    private List<String> collectMatchResultIds(String matchTaskId) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:596:        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:608:                    List<MatchResult> mrs = shep.getMatchResults(child);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:610:                    for (MatchResult mr : mrs) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:621:                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
src/main/java/org/ecocean/ia/MatchResultProspect.java:15:public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
src/main/java/org/ecocean/ia/MatchResultProspect.java:20:    private MatchResult matchResult;
src/main/java/org/ecocean/ia/MatchResultProspect.java:22:    public MatchResultProspect() {}
src/main/java/org/ecocean/ia/MatchResultProspect.java:24:    public MatchResultProspect(Annotation ann) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:29:    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
src/main/java/org/ecocean/ia/MatchResultProspect.java:40:     * prospects after the MatchResult has been persisted (empty-match-
src/main/java/org/ecocean/ia/MatchResultProspect.java:85:        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
src/main/java/org/ecocean/ia/MatchResultProspect.java:97:    @Override public int compareTo(MatchResultProspect other) {
src/main/java/org/ecocean/ia/MatchResult.java:34:public class MatchResult implements java.io.Serializable {
src/main/java/org/ecocean/ia/MatchResult.java:38:    private Set<MatchResultProspect> prospects;
src/main/java/org/ecocean/ia/MatchResult.java:50:    // number of MatchResultProspects [per type] to actually store (hotspotter
src/main/java/org/ecocean/ia/MatchResult.java:54:    public MatchResult() {
src/main/java/org/ecocean/ia/MatchResult.java:59:    public MatchResult(Task task) {
src/main/java/org/ecocean/ia/MatchResult.java:64:    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:70:    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
src/main/java/org/ecocean/ia/MatchResult.java:77:    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
src/main/java/org/ecocean/ia/MatchResult.java:166:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:182:            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
src/main/java/org/ecocean/ia/MatchResult.java:208:            this.prospects = new HashSet<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:215:                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
src/main/java/org/ecocean/ia/MatchResult.java:255:            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
src/main/java/org/ecocean/ia/MatchResult.java:266:    public Set<MatchResultProspect> getProspects() {
src/main/java/org/ecocean/ia/MatchResult.java:489:     * <p>Package-visible so {@code MatchResultPairxPayloadTest} can
src/main/java/org/ecocean/ia/MatchResult.java:539:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:546:    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
src/main/java/org/ecocean/ia/MatchResult.java:548:        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/ia/MatchResult.java:551:        for (MatchResultProspect mrp : prospects) {
src/main/java/org/ecocean/ia/MatchResult.java:564:            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
src/main/java/org/ecocean/ia/MatchResult.java:646:        String s = "MatchResult " + id;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:19:import org.ecocean.ia.MatchResult;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:20:import org.ecocean.ia.MatchResultProspect;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2808:    public MatchResult getMatchResult(String id) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2809:        MatchResult mr = null;
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2812:            mr = (MatchResult)(pm.getObjectById(pm.newObjectIdInstance(MatchResult.class, id),
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2820:    public List<MatchResult> getMatchResults(Task task) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2821:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2824:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE task.id == '" + task.getId() +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2829:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2834:    public List<MatchResult> getMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2835:        List<MatchResult> all = new ArrayList<MatchResult>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2838:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2843:        if (c != null) all = new ArrayList<MatchResult>(c);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2848:    // faster deletion of all MatchResults associated with Annotation
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2849:    public long deleteMatchResults(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2852:        String filter = "SELECT FROM org.ecocean.ia.MatchResult WHERE queryAnnotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2857:        System.out.println("[DEBUG] deleteMatchResults() deleted " + ct + " [" + (System.currentTimeMillis() - t) + "ms] on " + ann);
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2861:    public List<MatchResultProspect> getMatchResultProspects(Annotation ann) {
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2862:        List<MatchResultProspect> all = new ArrayList<MatchResultProspect>();
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2865:        String filter = "SELECT FROM org.ecocean.ia.MatchResultProspect WHERE annotation.id == '" +
src/main/java/org/ecocean/shepherd/core/Shepherd.java:2869:        if (c != null) all = new ArrayList<MatchResultProspect>(c);
src/main/java/org/ecocean/servlet/AnnotationEdit.java:165:                        annot.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/AnnotationEdit.java:166:                        annot.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:107:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:108:                        ann.deleteMatchResultProspects(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:127:                        ann.deleteMatchResults(myShepherd);
src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java:128:                        ann.deleteMatchResultProspects(myShepherd);

 succeeded in 3075ms:
migrate-ml-service-v2
bcd4bea62 (HEAD -> migrate-ml-service-v2, origin/migrate-ml-service-v2) fix(react): SPA fallback returns HTTP 200 for navigation routes
32f87dd0a refactor(ia): PairX as Phase A/B/C — no Shepherd across HTTP
a9957d6e9 fix(ia): MatchResult PairX bbox clamp + shared-array fix
52f02117a feat(ia): rewire MlServiceProcessor through MatchVisibilityGate
47a92c3b5 feat(ia): MatchVisibilityGate + DeferredMatchPublisher
a86f09056 feat(ia): MatchEligibilityQuery direct-SQL helper
4289b4d53 feat(opensearch): waitForAnnotationMatchableIds matchable visibility
bf1adfbc5 feat(ia): Task.getStoredStatus raw side-effect-free accessor
1beeac354 feat(ia): registerOneByDto Phase 0 image POST
a75bee2b5 feat(ia): WbiaRegisterRequest image fields + Phase A capture
635b69006 refactor(ia): extract parseFancyUuidArrayStrict shared helper
6195a6ddc feat(ia): iaImageIdsStrict variant for WBIA Phase 0 already-registered check
c4085af42 feat(ia): promote WildbookIAM.mediaAssetToUri public + null-safe
a0a50b3b1 feat(cache): QueryCacheFactory.safeInvalidate null-safe helper
80062bd59 docs: design for empty match prospects fix (locked after Codex round 5)
b0f01f9ff docs: design for image-then-annotation WBIA registration
d3fb8b0f7 test(ia): MlServiceLiveIntegrationTest Codex post-scrub follow-ups
c8f122518 Updated integration testing
42606005e test(ia): MlServiceLiveIntegrationTest scrub + Codex review follow-ups
43e50e3e2 Merge branch 'main' into migrate-ml-service-v2
72c752bfa (origin/main, origin/HEAD, main) Merge pull request #1577 from WildMeOrg/fix/import-task-stats-jdo-bug
79562a5fb (fix/import-task-stats-jdo-bug) Fix a a DataNucleus 5.2.7 SQL-generation bug
1905c1a1e Merge pull request #1576 from WildMeOrg/fix/ia-callback-https-scheme
e86a8a59d (fix/ia-callback-https-scheme) fix(devops): extend X-Forwarded-Proto fix to nginx.conf (primary template)
e34b782c2 fix(devops): make Tomcat honor proxy scheme so IA callback URL uses HTTPS

 succeeded in 12735ms:
 M .github/workflows/claude-code-review.yml
 M .github/workflows/claude.yml
 M .gitignore
 M archive/copyBuild.sh
 M archive/imageTransformWrapper.sh
 M archive/sql/ml_service_embedding_audit.sql
 M archive/sql/ml_service_idempotency.sql
 M config/git-json-info.ps1
 M config/git-json-info.sh
 M devops/deploy/.dockerfiles/alloy/config.alloy
 M devops/deploy/.dockerfiles/alloy/config.tempo.alloy
 M devops/deploy/.dockerfiles/db/postgresql.conf
 M devops/deploy/.dockerfiles/nginx/nginx-https.conf
 M devops/deploy/.dockerfiles/nginx/nginx.conf
 M devops/deploy/.dockerfiles/opensearch/log4j2.properties
 M devops/deploy/.dockerfiles/tomcat/server.xml
 M devops/deploy/_env.template
 M devops/deploy/docker-compose.yml
 M devops/development/.dockerfiles/tomcat/server.xml
 M devops/development/docker-compose.yml
 M docs/plans/2026-05-09-ml-service-migration-v2.md
 M docs/plans/2026-05-18-wbia-image-registration-design.md
 M frontend/maven-build.sh
 M frontend/package-lock.json
 M frontend/package.json
 M frontend/src/App.jsx
 M frontend/src/AuthenticatedSwitch.jsx
 M frontend/src/FrontDesk.jsx
 M frontend/src/SiteSettingsContext.jsx
 M frontend/src/UnAuthenticatedSwitch.jsx
 M frontend/src/__tests__/FrontDesk.test.js
 M frontend/src/__tests__/components/AddAdditionalModal.test.js
 M frontend/src/__tests__/components/AuthenticatedSwitch.test.js
 M frontend/src/__tests__/components/Map.test.js
 M frontend/src/__tests__/components/SearchAndSelectInput.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportEditableDataTable.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportImageUpload.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportInstuctionsModal.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportStore.test.js
 M frontend/src/__tests__/pages/BulkImport/BulkImportTask.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoCard.test.js
 M frontend/src/__tests__/pages/Encounter/ContactInfoModal.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/DateSectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/EditAnnotation.test.js
 M frontend/src/__tests__/pages/Encounter/Encounter.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterPageViewOnly.test.js
 M frontend/src/__tests__/pages/Encounter/EncounterStore.test.js
 M frontend/src/__tests__/pages/Encounter/HelperFunctions.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/IdentifySectionReview.test.js
 M frontend/src/__tests__/pages/Encounter/ImageCard.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModal.test.js
 M frontend/src/__tests__/pages/Encounter/ImageModalStore.test.js
 M frontend/src/__tests__/pages/Encounter/LocationSectionEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MapDisplay.test.js
 M frontend/src/__tests__/pages/Encounter/MatchCriteria.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsEdit.test.js
 M frontend/src/__tests__/pages/Encounter/MeasurementsReview.test.js
 M frontend/src/__tests__/pages/Encounter/MoreDetails.test.js
 M frontend/src/__tests__/pages/Encounter/NewMatchStore.test.js
 M frontend/src/__tests__/pages/Encounter/ProjectsCard.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/BiologicalSamplesAndAnalysesFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/CalenderView.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/DateFilter.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterFormStore.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/EncounterSearch.test.js
 M frontend/src/__tests__/pages/EncounterSearchPageAndFilters/IndividualDateFilter.test.js
 M frontend/src/__tests__/pages/LandingPage/LandingPage.test.js
 M frontend/src/__tests__/pages/LandingPage/PickUpWhereYouLeft.test.js
 M frontend/src/__tests__/pages/ManualAnnotationPage/ManualAnnotation.test.js
 M frontend/src/__tests__/pages/MatchResults/CreateNewIndividualModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/InstructionsModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchConfirmedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchCriteriaDrawer.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx
 M frontend/src/__tests__/pages/MatchResults/MatchResultsBottomBar.test.jsx
 M frontend/src/__tests__/pages/MatchResults/NewIndividualCreatedModal.test.jsx
 M frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js
 M frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js
 M frontend/src/__tests__/pages/PoliciesAndData.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ImageSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/PlaceSection.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportAnEncounter.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/ReportEncounterStore.test.js
 M frontend/src/__tests__/pages/ReportAnEncounterPage/SpeciesSection.test.js
 M frontend/src/__tests__/pages/login/LoginPageAuthenticate.test.js
 M frontend/src/__tests__/pages/login/LoginPageButtonState.test.js
 M frontend/src/__tests__/pages/login/LoginPageError.test.js
 M frontend/src/__tests__/pages/login/LoginPageInput.test.js
 M frontend/src/__tests__/pages/login/LoginPageLinks.test.js
 M frontend/src/__tests__/pages/login/LoginPagePasswordToggle.test.js
 M frontend/src/__tests__/pages/login/LoginPageRender.test.js
 M frontend/src/__tests__/pages/login/LoginPageSubmit.test.js
 M frontend/src/components/AnnotationOverlay.jsx
 M frontend/src/components/AuthenticatedAppHeader.jsx
 M frontend/src/components/Chip.jsx
 M frontend/src/components/ContainerWithSpinner.jsx
 M frontend/src/components/DataTable.jsx
 M frontend/src/components/FilterPanel.jsx
 M frontend/src/components/Footer.jsx
 M frontend/src/components/Form/FormGroupMultiSelect.jsx
 M frontend/src/components/ImageModal.jsx
 M frontend/src/components/LoadingScreen.jsx
 M frontend/src/components/Map.jsx
 M frontend/src/components/MultiSelectWithCheckbox.jsx
 M frontend/src/components/SimpleDataTable.jsx
 M frontend/src/components/SmallSpinner.jsx
 M frontend/src/components/UnAuthenticatedAppHeader.jsx
 M frontend/src/components/filterFields/BiologicalSamplesAndAnalysesFilter.jsx
 M frontend/src/components/filterFields/DateFilter.jsx
 M frontend/src/components/filterFields/ImageLabelFilter.jsx
 M frontend/src/components/filterFields/IndividualsObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/LocationFilterMap.jsx
 M frontend/src/components/filterFields/LocationFilterText.jsx
 M frontend/src/components/filterFields/MetadataFilter.jsx
 M frontend/src/components/filterFields/ObservationAttributeFilter.jsx
 M frontend/src/components/filterFields/SocialFilter.jsx
 M frontend/src/components/generalInputs/CoordinatesInput.jsx
 M frontend/src/components/header/HeaderDropdownItems.jsx
 M frontend/src/components/header/Menu.jsx
 M frontend/src/components/home/PickUpWhereYouLeft.jsx
 M frontend/src/components/icons/EditIcon.jsx
 M frontend/src/components/icons/EncounterIcon.jsx
 M frontend/src/components/icons/ExitIcon.jsx
 M frontend/src/components/icons/FullscreenIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon.jsx
 M frontend/src/components/icons/SpotMappingIcon2.jsx
 M frontend/src/constants/navMenu.js
 M frontend/src/hooks/useDocumentTitle.js
 M frontend/src/locale/de.json
 M frontend/src/locale/en.json
 M frontend/src/locale/es.json
 M frontend/src/locale/fr.json
 M frontend/src/locale/it.json
 M frontend/src/models/encounters/useFilterEncountersWithMediaAssets.js
 M frontend/src/pages/AboutUs.jsx
 M frontend/src/pages/BulkImport/BulkImportErrorSummaryBar.jsx
 M frontend/src/pages/BulkImport/BulkImportImageUpload.jsx
 M frontend/src/pages/BulkImport/BulkImportInstructionsModal.jsx
 M frontend/src/pages/BulkImport/BulkImportStore.js
 M frontend/src/pages/BulkImport/BulkImportTask.jsx
 M frontend/src/pages/BulkImport/EditableDataTable.jsx
 M frontend/src/pages/Citation.jsx
 M frontend/src/pages/EditAnnotation.jsx
 M frontend/src/pages/Encounter/AddPeople.jsx
 M frontend/src/pages/Encounter/AttributesSectionEdit.jsx
 M frontend/src/pages/Encounter/ContactInfoCard.jsx
 M frontend/src/pages/Encounter/ContactInfoModal.jsx
 M frontend/src/pages/Encounter/Encounter.jsx
 M frontend/src/pages/Encounter/IdentifySectionEdit.jsx
 M frontend/src/pages/Encounter/IdentifySectionReview.jsx
 M frontend/src/pages/Encounter/ImageCard.jsx
 M frontend/src/pages/Encounter/LocationSectionEdit.jsx
 M frontend/src/pages/Encounter/MapDisplay.jsx
 M frontend/src/pages/Encounter/MatchCriteria.jsx
 M frontend/src/pages/Encounter/MetadataSectionEdit.jsx
 M frontend/src/pages/Encounter/MoreDetails.jsx
 M frontend/src/pages/Encounter/ProjectsCard.jsx
 M frontend/src/pages/Encounter/SpotMappingCard.jsx
 M frontend/src/pages/Encounter/constants.js
 M frontend/src/pages/Encounter/pollingHelpers.js
 M frontend/src/pages/Encounter/stores/EncounterStore.js
 M frontend/src/pages/Encounter/stores/NewMatchStore.js
 M frontend/src/pages/Encounter/stores/helperFunctions.js
 M frontend/src/pages/HowToPhotograph.jsx
 M frontend/src/pages/Login.jsx
 M frontend/src/pages/ManualAnnotation.jsx
 M frontend/src/pages/MatchResultsPage/MatchResults.jsx
 M frontend/src/pages/MatchResultsPage/components/CreateNewIndividualModal.jsx
 M frontend/src/pages/MatchResultsPage/components/EmptyMatchPlaceholder.jsx
 M frontend/src/pages/MatchResultsPage/components/InspectorModal.jsx
 M frontend/src/pages/MatchResultsPage/components/InstructionsModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchConfirmedModal.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchCriteriaDrawer.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
 M frontend/src/pages/MatchResultsPage/components/MatchResultsBottomBar.jsx
 M frontend/src/pages/MatchResultsPage/components/NewIndividualCreatedModal.jsx
 M frontend/src/pages/MatchResultsPage/constants.js
 M frontend/src/pages/MatchResultsPage/helperFunctions.js
 M frontend/src/pages/MatchResultsPage/icons/ExitFullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FilterIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/FullScreenIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/HatchMarkIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/InfoIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ToggleAnnotationIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomInIcon.jsx
 M frontend/src/pages/MatchResultsPage/icons/ZoomOutIcon.jsx
 M frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
 M frontend/src/pages/PoliciesAndData/PoliciesAndData.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ImageSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/PlaceSection.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounter.jsx
 M frontend/src/pages/ReportsAndManagamentPages/ReportEncounterStore.js
 M frontend/src/pages/ReportsAndManagamentPages/SpeciesSection.jsx
 M frontend/src/pages/SearchPages/EncounterSearch.jsx
 M frontend/src/pages/SearchPages/components/ExportModal.jsx
 M frontend/src/pages/SearchPages/searchResultTabs/GalleryView.jsx
 M frontend/src/pages/SearchPages/stores/EncounterFormStore.js
 M frontend/src/pages/SearchPages/stores/ImageModalStore.js
 M frontend/src/service-worker.js
 M pom.xml
 M src/main/java/org/ecocean/Annotation.java
 M src/main/java/org/ecocean/AnnotationLite.java
 M src/main/java/org/ecocean/Base.java
 M src/main/java/org/ecocean/CommonConfiguration.java
 M src/main/java/org/ecocean/EmailTemplate.java
 M src/main/java/org/ecocean/Embedding.java
 M src/main/java/org/ecocean/Encounter.java
 M src/main/java/org/ecocean/IAJsonProperties.java
 M src/main/java/org/ecocean/ImageProcessor.java
 M src/main/java/org/ecocean/LocationID.java
 M src/main/java/org/ecocean/MarkedIndividual.java
 M src/main/java/org/ecocean/NotificationMailer.java
 M src/main/java/org/ecocean/Occurrence.java
 M src/main/java/org/ecocean/RestClient.java
 M src/main/java/org/ecocean/SpotterConserveIO.java
 M src/main/java/org/ecocean/Survey.java
 M src/main/java/org/ecocean/User.java
 M src/main/java/org/ecocean/Util.java
 M src/main/java/org/ecocean/acm/AcmUtil.java
 M src/main/java/org/ecocean/api/GenericObject.java
 M src/main/java/org/ecocean/api/Login.java
 M src/main/java/org/ecocean/api/Logout.java
 M src/main/java/org/ecocean/api/MarkedIndividualInfo.java
 M src/main/java/org/ecocean/api/SiteSettings.java
 M src/main/java/org/ecocean/api/bulk/BulkImportUtil.java
 M src/main/java/org/ecocean/api/patch/EncounterPatchValidator.java
 M src/main/java/org/ecocean/export/EncounterCOCOExportFile.java
 M src/main/java/org/ecocean/grid/AppletHeartbeatThread.java
 M src/main/java/org/ecocean/grid/EncounterLite.java
 M src/main/java/org/ecocean/grid/GridManager.java
 M src/main/java/org/ecocean/grid/MatchGraphCreationThread.java
 M src/main/java/org/ecocean/grid/MatchedPoints.java
 M src/main/java/org/ecocean/grid/SpotTriangle.java
 M src/main/java/org/ecocean/grid/WorkAppletHeadlessEpic.java
 M src/main/java/org/ecocean/ia/IA.java
 M src/main/java/org/ecocean/ia/IAException.java
 M src/main/java/org/ecocean/ia/MLService.java
 M src/main/java/org/ecocean/ia/MlServiceClient.java
 M src/main/java/org/ecocean/ia/MlServiceJobOutcome.java
 M src/main/java/org/ecocean/identity/IBEISIA.java
 M src/main/java/org/ecocean/identity/IdentityServiceLog.java
 M src/main/java/org/ecocean/media/AssetStore.java
 M src/main/java/org/ecocean/media/AssetStoreConfig.java
 M src/main/java/org/ecocean/media/AssetStoreFactory.java
 M src/main/java/org/ecocean/media/Feature.java
 M src/main/java/org/ecocean/media/LocalAssetStore.java
 M src/main/java/org/ecocean/media/MediaAsset.java
 M src/main/java/org/ecocean/mmutil/FileUtilities.java
 M src/main/java/org/ecocean/mmutil/MediaUtilities.java
 M src/main/java/org/ecocean/movement/Path.java
 M src/main/java/org/ecocean/opendata/OBISSeamap.java
 M src/main/java/org/ecocean/opendata/Share.java
 M src/main/java/org/ecocean/resumableupload/UploadServlet.java
 M src/main/java/org/ecocean/security/ShepherdRealm.java
 M src/main/java/org/ecocean/servlet/AnnotationEdit.java
 M src/main/java/org/ecocean/servlet/EncounterDelete.java
 M src/main/java/org/ecocean/servlet/EncounterForm.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveAnnotation.java
 M src/main/java/org/ecocean/servlet/EncounterRemoveSpots.java
 M src/main/java/org/ecocean/servlet/GrothMatchServlet.java
 M src/main/java/org/ecocean/servlet/IAGateway.java
 M src/main/java/org/ecocean/servlet/IAGlobals.java
 M src/main/java/org/ecocean/servlet/MediaAssetModify.java
 M src/main/java/org/ecocean/servlet/SiteSearch.java
 M src/main/java/org/ecocean/servlet/SubmitSpotsAndImage.java
 M src/main/java/org/ecocean/servlet/UserCreate.java
 M src/main/java/org/ecocean/servlet/UserResetPassword.java
 M src/main/java/org/ecocean/servlet/WorkspaceDelete.java
 M src/main/java/org/ecocean/servlet/export/EncounterSearchExportCOCO.java
 M src/main/java/org/ecocean/servlet/export/IndividualSearchExportCapture.java
 M src/main/java/org/ecocean/servlet/export/MarkRecaptureEncounters.java
 M src/main/java/org/ecocean/servlet/export/SimpleCMROutput.java
 M src/main/java/org/ecocean/servlet/importer/DeleteImportTask.java
 M src/main/java/org/ecocean/servlet/importer/ImportTask.java
 M src/main/java/org/ecocean/servlet/importer/StandardImport.java
 M src/main/java/org/ecocean/shepherd/core/Shepherd.java
 M src/main/java/org/ecocean/shepherd/core/ShepherdProperties.java
 M src/main/resources/bundles/IA.json
 M src/main/resources/bundles/IA.properties
 M src/main/resources/bundles/commonConfiguration.properties
 M src/main/resources/bundles/de/commonConfigurationLabels.properties
 M src/main/resources/bundles/de/header.properties
 M src/main/resources/bundles/en/commonConfigurationLabels.properties
 M src/main/resources/bundles/en/header.properties
 M src/main/resources/bundles/en/index.properties
 M src/main/resources/bundles/en/occurrence.properties
 M src/main/resources/bundles/en/survey.properties
 M src/main/resources/bundles/es/commonConfigurationLabels.properties
 M src/main/resources/bundles/es/header.properties
 M src/main/resources/bundles/es/index.properties
 M src/main/resources/bundles/fr/commonConfigurationLabels.properties
 M src/main/resources/bundles/fr/encounter.properties
 M src/main/resources/bundles/fr/encounterSearch.properties
 M src/main/resources/bundles/fr/header.properties
 M src/main/resources/bundles/fr/index.properties
 M src/main/resources/bundles/haplotypeColorCodes.properties
 M src/main/resources/bundles/it/commonConfigurationLabels.properties
 M src/main/resources/bundles/it/header.properties
 M src/main/resources/bundles/it/index.properties
 M src/main/resources/bundles/locationID.json
 M src/main/resources/bundles/locationIDGPS.properties
 M src/main/resources/bundles/locationID_indocet.json
 M src/main/resources/bundles/newIndividualNumbers.properties
 M src/main/resources/bundles/opendata.properties
 M src/main/resources/log4j2.xml
 M src/main/resources/org/ecocean/ia/package.jdo
 M src/main/resources/org/ecocean/media/package.jdo
 M src/main/resources/org/ecocean/package.jdo
 M src/main/resources/servletResponseTemplate.htm
 M src/main/webapp/appadmin/catchUpEmbeddings.jsp
 M src/main/webapp/appadmin/importEmbeddings.jsp
 M src/main/webapp/appadmin/testEmbeddingSearch.jsp
 M src/main/webapp/contactus.jsp
 M src/main/webapp/css/imageEnhancer.css
 M src/main/webapp/cust/mantamatcher/styles/_wildbook.less
 M src/main/webapp/datacleaning/individuals.jsp
 M src/main/webapp/encounters/biologicalSamples.jsp
 M src/main/webapp/encounters/encounterCropTool.jsp
 M src/main/webapp/encounters/encounterSpotTool.jsp
 M src/main/webapp/encounters/manualAnnotation.jsp
 M src/main/webapp/encounters/scanEndApplet.jsp
 M src/main/webapp/encounters/spotMatchingAlgorithm.jsp
 M src/main/webapp/header.jsp
 M src/main/webapp/iaResults.jsp
 M src/main/webapp/import.jsp
 M src/main/webapp/import/photos.jsp
 M src/main/webapp/import/reviewDirectory.jsp
 M src/main/webapp/import/spreadsheet.jsp
 M src/main/webapp/index.jsp
 M src/main/webapp/javascript/ia.IBEIS.js
 M src/main/webapp/javascript/relationshipDiagrams/graphAbstract.js
 M src/main/webapp/listImages.jsp
 M src/main/webapp/logout.jsp
 M src/main/webapp/obrowse.jsp
 M src/main/webapp/occurrence.jsp
 M src/main/webapp/pictureBook.jsp
 M src/main/webapp/projects/project.jsp
 M src/main/webapp/submit.jsp
 M src/main/webapp/surveys/survey.jsp
 M src/main/webapp/surveys/surveyMapEmbed.jsp
 M src/main/webapp/welcome.jsp
 M src/test/java/org/ecocean/AnnotationTest.java
 M src/test/java/org/ecocean/EmbeddingTest.java
 M src/test/java/org/ecocean/IAJsonPropertiesTest.java
 M src/test/java/org/ecocean/MatchResultTest.java
 M src/test/java/org/ecocean/OpenSearchVisibilityTest.java
 M src/test/java/org/ecocean/UtilTest.java
 M src/test/java/org/ecocean/api/EncounterApiTest.java
 M src/test/java/org/ecocean/api/EncounterExportImagesTest.java
 M src/test/java/org/ecocean/api/SettingApiTest.java
 M src/test/java/org/ecocean/api/TaskMatchResults.java
 M src/test/java/org/ecocean/export/EncounterCOCOExportFileTest.java
 M src/test/java/org/ecocean/grid/GrothOptimizationTest.java
 M src/test/java/org/ecocean/grid/GrothParameterSweepTest.java
 M src/test/java/org/ecocean/grid/MatchGraphCacheTest.java
 M src/test/java/org/ecocean/ia/IdentificationTest.java
 M src/test/java/org/ecocean/ia/MlServiceClientTest.java
 M src/test/java/org/ecocean/ia/MlServiceJobOutcomeTest.java
 M src/test/java/org/ecocean/ia/MlServiceProcessorTest.java
 M src/test/java/org/ecocean/ia/TaskStatusInEndStateTest.java
 M src/test/java/org/ecocean/media/MediaAssetDetectionStatusTest.java
 M src/test/resources/log4j2-test.xml
?? IA.json
?? logs/

codex
The backend response builder does produce the wrapper the store expects: `prospects` is a JSON object keyed by score type, typically `annot` and `indiv`. The more suspicious gap is now in the store/view contract: the page starts in `individual` mode and `hasDisplaySections` only checks the current mode, so any state with only `annot` prospects can be hidden behind the page-level empty state even though `hasResults` is true. I’m checking whether the live vector path can legitimately produce only `annot` prospects, and whether tests cover the actual component row shape.
exec
/bin/bash -lc "sed -n '360,405p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '200,270p' src/main/java/org/ecocean/ia/MatchResult.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,280p' frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '72,120p' src/test/java/org/ecocean/MatchResultTest.java" in /mnt/c/Wildbook-clean2
 succeeded in 250ms:
                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                    " due to no suitable embeddings for " + iaConfig);
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("REQUIRED",
                    "no suitable embeddings for getMatches()");
                subTask.setCompletionDateInMilliseconds();
                myShepherd.getPM().makePersistent(subTask);
                subtasksFailed++;
                continue; // on to next ann
            }
            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
            System.out.println("findMatchProspects() on " + ann + " found " +
                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
                " candidates) for subTask " + subTask.getId());
            boolean mrOk = true;
            try {
                // we build this even if empty, cuz that means we got results; just not nice ones
                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
                myShepherd.getPM().makePersistent(mr);
            } catch (IOException ex) {
                System.out.println("findMatchProspects() MatchResult creation failed on " +
                    subTask + ": " + ex);
                ex.printStackTrace();
                mrOk = false;
            }
            if (mrOk) {
                subTask.setStatus("completed");
                subtasksOk++;
            } else {
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("UNKNOWN",
                    "MatchResult persistence failed");
                subtasksFailed++;
            }
            subTask.setCompletionDateInMilliseconds();
            myShepherd.getPM().makePersistent(subTask);
        }
        // Reflect subtask outcomes on the parent. Any failure marks the
        // parent "error" with a PARTIAL detail; otherwise "completed".
        // No "partial" terminal status is introduced — Task.statusInEndState
        // only recognizes completed/error, so adding one would ripple into
        // polling/UI semantics.
        if (subtasksFailed == 0) {
            task.setStatus("completed");
        } else {

 succeeded in 258ms:
    // outer Shepherd is never held across the PairX HTTP call. Holding a
    // Shepherd across that ~10-30s POST would risk connection-pool
    // exhaustion under load (Codex C12 review High).
    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
        Shepherd myShepherd)
    throws IOException {
        if (Util.collectionIsEmptyOrNull(annots)) return 0;
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        if (scoreByIndividual) {
            // the scores for these are calculated weighted by indiv count
            _populateProspectsByIndividual(annots, myShepherd);
        } else {
            // these scores are direct from opensearch
            for (Annotation ann : annots) {
                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
                    null));
            }
        }
        this.numberProspects = this.prospects.size();
        return this.numberProspects;
    }

    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
            List<Annotation> >();

        for (Annotation ann : annots) {
            Encounter enc = ann.findEncounter(myShepherd);
            // i think we just ignore if no enc/indiv
            if (enc == null) continue;
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv == null) continue;
            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
            tally.get(indiv).add(ann);
        }
        if (tally.size() < 1) return; // no individuals i guess?

        // this sorts by most annots (per indiv) highest to lowest
        List<Map.Entry<MarkedIndividual,
            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
            Map.Entry<MarkedIndividual, List<Annotation> > two) {
                // we reverse order here so we get largest first
                return Integer.compare(two.getValue().size(), one.getValue().size());
            }
        });
        int most = sorted.get(0).getValue().size(); // top num of annots
        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
            double score = new Double(ent.getValue().size()) / new Double(most);
            // the ent value (annot List) should always have at least one annot, so we use first one.
            // Inspection MediaAsset attached later by MatchInspectionPairxEnricher
            // (empty-match-prospects design Track 2 C13).
            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
                null));
        }
    }

    /**
     * Public read-only view of the prospects collection so the
     * {@link MatchInspectionPairxEnricher} can iterate them in Phase A
     * and Phase C without reaching into private state. Returns the
     * underlying Set; callers must not mutate.
     */
    public Set<MatchResultProspect> getProspects() {
        return this.prospects;
    }

    /**

 succeeded in 249ms:
        assertTrue(pj.getJSONArray("annot").getJSONObject(1).getDouble("score") == 0.1);
        JSONObject full = mr.jsonForApiGet(-1, null, myShepherd);
        assertTrue(full.getInt("numberTotalProspects") == 4);
        assertTrue(full.getInt("numberCandidates") == 3);
    }

    // annotation-list style creation
    @Test void testMatchResultVector()
    throws IOException {
        Task task = mock(Task.class);

        when(task.countObjectAnnotations()).thenReturn(1);
        int numCand = 99;
        Annotation ann = mock(Annotation.class);
        ArrayList<Annotation> annList = new ArrayList<Annotation>();

        annList.add(ann);
        when(task.getObjectAnnotations()).thenReturn(annList);

        MatchResult mr = new MatchResult(task, annList, numCand, null);
        assertTrue(mr.getNumberCandidates() == numCand);
        assertTrue(mr.numberProspects() == 1);
        // FIXME someday we need to figure out indiv-vector-search
        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
        assertTrue(mr.prospectScoreTypes().contains("annot"));
    }

    @Test void basicMatchResultProspect() {
        MatchResultProspect mrp = new MatchResultProspect(null, 1.0, "test", null);

        assertNotNull(mrp);
        assertTrue(mrp.getScore() == 1.0);
        assertEquals(mrp.getType(), "test");
        assertTrue(mrp.isType("test"));
        assertFalse(mrp.isType(null));
        // null annotation allows us to get away with null shepherd passed here
        // as annotationDetails() will simply return empty json for no annot
        JSONObject json = mrp.jsonForApiGet(null);
        assertTrue(json.getDouble("score") == 1.0);
    }
}

 succeeded in 255ms:
import {
  getAllAnnot,
  getAllIndiv,
} from "../../../pages/MatchResultsPage/helperFunctions";

describe("helperFunctions", () => {
  const makeNode = (overrides = {}) => ({
    id: "task-1",
    status: "complete",
    statusOverall: "complete",
    dateCreated: "2024-01-01",
    method: { name: "hotspotter", description: "HotSpotter algorithm" },
    matchingSetFilter: { species: "whale" },
    matchResults: {
      numberCandidates: 5,
      queryAnnotation: {
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
        asset: { url: "http://example.com/img.jpg" },
        encounter: { id: "enc-1", locationId: "loc-1" },
        individual: { id: "ind-1", displayName: "Willy" },
      },
      prospects: {
        annot: [{ annotId: "a1", score: 0.9 }],
        indiv: [{ individualId: "i1", score: 0.85 }],
      },
    },
    children: [],
    ...overrides,
  });

  describe("getAllAnnot", () => {
    test("returns empty array for null node", () => {
      expect(getAllAnnot(null)).toEqual([]);
    });

    test("returns empty array for undefined node", () => {
      expect(getAllAnnot(undefined)).toEqual([]);
    });

    test("returns common object when annot prospects is empty but task is running", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { annot: [], indiv: [{ individualId: "i1", score: 0.9 }] },
        },
      });
      // statusOverall is "complete" (not "completed"), so task is considered still running
      const result = getAllAnnot(node);
      expect(result).toHaveLength(1);
      expect(result[0].hasResults).toBe(false);
      expect(result[0].taskId).toBe("task-1");
    });

    test("returns common object when annot prospects is missing but task is running", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { indiv: [{ individualId: "i1" }] },
        },
      });
      // statusOverall is "complete" (not "completed"), so task is considered still running
      const result = getAllAnnot(node);
      expect(result).toHaveLength(1);
      expect(result[0].hasResults).toBe(false);
      expect(result[0].taskId).toBe("task-1");
    });

    test("collects annot prospects and attaches common fields", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      expect(result).toHaveLength(1);
      const item = result[0];
      expect(item.annotId).toBe("a1");
      expect(item.score).toBe(0.9);
      expect(item.algorithm).toBe("hotspotter");
      expect(item.methodDescription).toBe("HotSpotter algorithm");
      expect(item.taskId).toBe("task-1");
      expect(item.taskStatus).toBe("complete");
      expect(item.taskStatusOverall).toBe("complete");
      expect(item.date).toBe("2024-01-01");
      expect(item.numberCandidates).toBe(5);
      expect(item.queryEncounterId).toBe("enc-1");
      expect(item.encounterLocationId).toBe("loc-1");
      expect(item.queryIndividualId).toBe("ind-1");
      expect(item.queryIndividualDisplayName).toBe("Willy");
      expect(item.queryEncounterImageUrl).toBe("http://example.com/img.jpg");
      expect(item.matchingSetFilter).toEqual({ species: "whale" });
      expect(item.hasResults).toBe(true);
    });

    test("attaches queryEncounterAnnotation with correct shape", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      expect(result[0].queryEncounterAnnotation).toEqual({
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
      });
    });

    test("uses method.description as methodName when method.name is undefined", () => {
      const node = makeNode({ method: { description: "FlukeMatcher" } });
      const result = getAllAnnot(node);
      expect(result[0].algorithm).toBe("FlukeMatcher");
      expect(result[0].methodName).toBe("FlukeMatcher");
    });

    test("recurses into children", () => {
      const child = makeNode({
        id: "task-2",
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [{ annotId: "a2", score: 0.7 }],
            indiv: [],
          },
        },
      });
      const node = makeNode({ children: [child] });
      const result = getAllAnnot(node);
      expect(result).toHaveLength(2);
      expect(result.map((r) => r.annotId)).toContain("a2");
    });

    test("returns common object when no matchResults but task is running", () => {
      const node = makeNode({ matchResults: null });
      // statusOverall is "complete" (not "completed"), so task is considered still running
      const result = getAllAnnot(node);
      expect(result).toHaveLength(1);
      expect(result[0].hasResults).toBe(false);
      expect(result[0].taskId).toBe("task-1");
    });

    test("handles multiple annot prospects in one node", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [
              { annotId: "a1", score: 0.9 },
              { annotId: "a2", score: 0.8 },
              { annotId: "a3", score: 0.7 },
            ],
            indiv: [],
          },
        },
      });
      expect(getAllAnnot(node)).toHaveLength(3);
    });

    test("sets displayIndex correctly via spread (items keep own keys)", () => {
      const node = makeNode();
      const result = getAllAnnot(node);
      // common fields should override item fields if same key
      expect(result[0].hasResults).toBe(true);
    });
  });

  describe("getAllIndiv", () => {
    test("returns empty array for null node", () => {
      expect(getAllIndiv(null)).toEqual([]);
    });

    test("returns common object when indiv prospects is empty but task is running", () => {
      const node = makeNode({
        matchResults: {
          ...makeNode().matchResults,
          prospects: { annot: [{ annotId: "a1" }], indiv: [] },
        },
      });
      // statusOverall is "complete" (not "completed"), so task is considered still running
      const result = getAllIndiv(node);
      expect(result).toHaveLength(1);
      expect(result[0].hasResults).toBe(false);
      expect(result[0].taskId).toBe("task-1");
    });

    test("collects indiv prospects and attaches common fields", () => {
      const node = makeNode();
      const result = getAllIndiv(node);
      expect(result).toHaveLength(1);
      const item = result[0];
      expect(item.individualId).toBe("i1");
      expect(item.score).toBe(0.85);
      expect(item.algorithm).toBe("hotspotter");
      expect(item.taskId).toBe("task-1");
      expect(item.hasResults).toBe(true);
    });

    test("recurses into multiple levels of children", () => {
      const grandchild = makeNode({
        id: "task-3",
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [],
            indiv: [{ individualId: "i3", score: 0.6 }],
          },
        },
      });
      const child = makeNode({
        id: "task-2",
        children: [grandchild],
        matchResults: {
          ...makeNode().matchResults,
          prospects: {
            annot: [],
            indiv: [{ individualId: "i2", score: 0.7 }],
          },
        },
      });
      const root = makeNode({ children: [child] });
      const result = getAllIndiv(root);
      expect(result).toHaveLength(3);
      expect(result.map((r) => r.individualId)).toEqual(
        expect.arrayContaining(["i1", "i2", "i3"]),
      );
    });

    test("numberCandidates defaults to 0 when missing", () => {
      const node = makeNode({
        matchResults: {
          queryAnnotation: makeNode().matchResults.queryAnnotation,
          prospects: { annot: [], indiv: [{ individualId: "i1" }] },
        },
      });
      expect(getAllIndiv(node)[0].numberCandidates).toBe("-");
    });

    test("queryEncounterId is null when encounter is absent", () => {
      const node = makeNode({
        matchResults: {
          numberCandidates: 3,
          queryAnnotation: { asset: null },
          prospects: {
            annot: [],
            indiv: [{ individualId: "i1" }],
          },
        },
      });
      const result = getAllIndiv(node);
      expect(result[0].queryEncounterId).toBeNull();
      expect(result[0].queryIndividualId).toBeNull();
      expect(result[0].queryEncounterImageUrl).toBeNull();
    });
  });
});

codex
There is a stronger backend/API root cause now: `Task.matchResultsJson()` only serializes `matchResults` inside `if (methodInfo != null)`, and `getIdentificationMethodInfo()` currently recognizes legacy `api_endpoint` or legacy WBIA `query_config_dict`. The locked v2 config explicitly allows `method` without `api_endpoint`, so v2 tasks can have persisted MatchResults that the API silently omits. I’m tracing task parameter creation to confirm this is the likely condition for your `method`-based v2 run.
exec
/bin/bash -lc 'rg -n "getIdentificationMethodInfo|matchResultsJson|api_endpoint|model_id|method" src/test/java/org/ecocean/api src/test/java/org/ecocean/ia src/test/java/org/ecocean/MatchResultTest.java src/test/java/org/ecocean/EmbeddingTest.java' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"ibeis.identification|api_endpoint|method\\\"|model_id|pipeline_root|_id_conf|_mlservice_conf|setParameters|addParameter\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/identity src/main/java/org/ecocean/servlet src/main/webapp -g'*.java' -g'*.jsp'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '520,630p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '730,780p' src/main/java/org/ecocean/ia/Task.java" in /mnt/c/Wildbook-clean2
 succeeded in 381ms:

    // capture original queue message to make this Task more easily resumeable
    public String getQueueResumeMessage() { return queueResumeMessage; }
    public void setQueueResumeMessage(String message) {
        if (message == null) { queueResumeMessage = null; } else {
            queueResumeMessage = message;
        }
    }

    public JSONObject getMatchingSetFilter() {
        if (getParameters() == null) return null;
        return getParameters().optJSONObject("matchingSetFilter");
    }

    public JSONObject getIdentificationMethodInfo() {
        if (getParameters() == null) return null;
        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
        JSONObject rtn = new JSONObject();
        // vector/embed flavor
        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
            null) != null) {
            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
                "model_id", null);
            if (modelId == null) {
                rtn.put("description", "Vector embedding match");
            } else {
                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
                rtn.put("modelId", modelId);
            }
            return rtn;
        }
        // it seems both of these are in most logs (and are identical), but being safe in case there are
        // examples in the wild with only one
        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
            "query_config_dict");
        if (conf == null)
            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
                "queryConfigDict");
        // we set HotSpotter if pipeline_root is not set here
        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
        rtn.put("description",
            getParameters().getJSONObject("ibeis.identification").optString("description",
            "unknown algorithm/method"));
        return rtn;
    }

    // convenience
    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
        return myShepherd.getMatchResults(this);
    }


 succeeded in 424ms:
            matchTask.setObjectAnnotations(anns);
            matchTask.addParameter("mlServiceV2Match", true);
            shep.getPM().makePersistent(matchTask);
            // findMatchProspects returns false when the match config is not
            // a vector config or matchConfig is null. Don't leave the match
            // task without a terminal status — mark the parent task error.
            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
            if (!ran) {
                matchTask.setStatus("error");
                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
                    "findMatchProspects rejected match config: " +
                    (matchConfig == null ? "null" : matchConfig.toString()));
                matchTask.setCompletionDateInMilliseconds();
                // Update the parent task in this same transaction (parent is
                // already loaded above) so the two updates commit atomically.
                // Splitting across transactions risks leaving the parent
                // "completed" if the second commit fails or the JVM dies.
                if (parent != null) {
                    markTaskError(parent, "INVALID_MATCH_CONFIG",
                        "no usable vector match config");
                }
                shep.commitDBTransaction();
                return MlServiceJobOutcome.validationError("INVALID_MATCH_CONFIG",
                    "no usable vector match config");
            }
            String matchTaskId = matchTask.getId();
            shep.commitDBTransaction();
            shep.rollbackAndClose();  // close BEFORE PairX enrichment (Track 2 C13)
            // Phase 4 (C13): PairX inspection-image enrichment. The
            // MatchResult + prospects are already persisted with
            // null inspection MediaAssets; the enricher fills them in
            // out-of-transaction via a Phase A/B/C flow per prospect.
            // Per-prospect failure is non-blocking — UI render works
            // either way, just without the inspection image.
            enrichPairxAssetsForMatchTask(matchTaskId);
            return MlServiceJobOutcome.ok(annotationIds);
        } catch (Exception ex) {
            markTaskError(taskId, "MATCH", "findMatchProspects failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("MATCH", ex.getMessage());
        } finally {
            shep.rollbackAndClose();
        }
    }

    /**
     * Phase 4: drive {@link MatchInspectionPairxEnricher} for every
     * MatchResult attached to a child of {@code matchTaskId}. Runs
     * after the main runMatchProspects transaction has closed, so the
     * PairX HTTP work doesn't hold a Shepherd. (Empty-match-prospects
     * design Track 2 C13.)
     */
    void enrichPairxAssetsForMatchTask(String matchTaskId) {
        if (matchTaskId == null) return;
        List<String> mrIds = collectMatchResultIds(matchTaskId);
        if (mrIds.isEmpty()) return;
        MatchInspectionPairxEnricher enricher =
            new MatchInspectionPairxEnricher(context);
        for (String mrId : mrIds) {
            try {
                enricher.enrichMatchResult(mrId);
            } catch (Exception ex) {
                System.out.println(
                    "[WARN] MlServiceProcessor.enrichPairxAssetsForMatchTask " +
                    "mr=" + mrId + " failed (non-blocking): " + ex);
            }
        }
    }

    /**
     * Open a short Shepherd, list MatchResult IDs attached to children
     * of {@code matchTaskId}, close. Returns scalar IDs only so
     * subsequent enrichment runs without DB state.
     */
    private List<String> collectMatchResultIds(String matchTaskId) {
        List<String> out = new ArrayList<String>();
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "collectMatchResultIds." + matchTaskId);
        try {
            shep.beginDBTransaction();
            Task matchTask = Task.load(matchTaskId, shep);
            if (matchTask == null) {
                shep.commitDBTransaction();
                return out;
            }
            List<Task> children = matchTask.getChildren();
            if (children != null) {
                for (Task child : children) {
                    if (child == null) continue;
                    List<MatchResult> mrs = shep.getMatchResults(child);
                    if (mrs == null) continue;
                    for (MatchResult mr : mrs) {
                        if (mr != null && mr.getId() != null) {
                            out.add(mr.getId());
                        }
                    }
                }
            }
            shep.commitDBTransaction();
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            System.out.println(
                "[WARN] MlServiceProcessor.collectMatchResultIds failed for " +
                matchTaskId + ": " + ex);
        } finally {
            shep.closeDBTransaction();
        }
        return out;
    }

    static MlServiceJobOutcome mapNonRetryableError(IAException ex) {
        String code = ex == null ? null : ex.getCode();

 succeeded in 816ms:
src/test/java/org/ecocean/EmbeddingTest.java:60:        // Neither method nor api_endpoint set → not a vector config.
src/test/java/org/ecocean/EmbeddingTest.java:66:        // New _id_conf contract: method/version present, no api_endpoint.
src/test/java/org/ecocean/EmbeddingTest.java:69:            .put("method", "miewid").put("version", "4.1")
src/test/java/org/ecocean/EmbeddingTest.java:75:        // Legacy: api_endpoint present, no method. Must keep working.
src/test/java/org/ecocean/EmbeddingTest.java:76:        JSONObject conf = new JSONObject().put("api_endpoint", "http://legacy");
src/test/java/org/ecocean/EmbeddingTest.java:81:        JSONObject conf = new JSONObject().put("method", "miewid");
src/test/java/org/ecocean/api/TaskMatchResults.java:151:        // test task.matchResultsJson() seperately so dont care about the null value here
src/test/java/org/ecocean/ia/IdentificationTest.java:51:            "{\"api_endpoint\": \"fake-mlservice-endpoint\", \"model_id\": \"method0-version0\" }"));
src/test/java/org/ecocean/ia/IdentificationTest.java:85:        conf = new JSONObject("{\"model_id\": \"abc-123\"}");
src/test/java/org/ecocean/ia/MlServiceProcessorTest.java:15: * <p>The Phase 1-5 lifecycle methods require real Shepherd transactions,
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:51:            "EMBEDDING join missing when method+version supplied: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:59:            "EMBEDDING join missing when only method supplied: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:70:        // Legacy api_endpoint-only config: gate just wants matchAgainst +
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:71:        // acmId reachable from the sibling MAs; no embedding-method filter.
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:94:            "expected strict '' equality for blank method: " + sql);
src/test/java/org/ecocean/ia/MatchEligibilityQueryTest.java:127:            "method not escaped: " + sql);
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:18: * methods. The full {@link MatchVisibilityGateImpl#gateForBatch}
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:240:    // --- gateForBatch public-method coverage -----------------------------
src/test/java/org/ecocean/ia/MatchVisibilityGateImplTest.java:257:            new org.json.JSONObject().put("method", "miewid-msv4.1").put("version", "4.1"),
src/test/java/org/ecocean/ia/MatchInspectionPairxEnricherTest.java:41:        assertEquals("miewid-msv4.1", p.getString("model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:37:            .put("predict_model_id", "msv3")
src/test/java/org/ecocean/ia/MlServiceClientTest.java:38:            .put("classify_model_id", "efnet")
src/test/java/org/ecocean/ia/MlServiceClientTest.java:39:            .put("extract_model_id", "miewid-4.1")
src/test/java/org/ecocean/ia/MlServiceClientTest.java:40:            .put("orientation_model_id", "densenet");
src/test/java/org/ecocean/ia/MlServiceClientTest.java:43:        assertEquals("msv3", p.getString("predict_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:44:        assertEquals("efnet", p.getString("classify_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:45:        assertEquals("miewid-4.1", p.getString("extract_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:46:        assertEquals("densenet", p.getString("orientation_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:50:        JSONObject conf = new JSONObject().put("predict_model_id", "msv3");
src/test/java/org/ecocean/ia/MlServiceClientTest.java:53:        assertEquals("msv3", p.getString("predict_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:54:        assertFalse(p.has("classify_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:55:        assertFalse(p.has("orientation_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:61:        JSONObject conf = new JSONObject().put("extract_model_id", "miewid-4.1");
src/test/java/org/ecocean/ia/MlServiceClientTest.java:65:        assertEquals("miewid-4.1", p.getString("extract_model_id"));
src/test/java/org/ecocean/ia/MlServiceClientTest.java:83:            .put("embedding_model_id", "miewid")
src/test/java/org/ecocean/ia/MlServiceClientTest.java:204:        result.remove("embedding_model_id");
src/test/java/org/ecocean/ia/MlServiceClientTest.java:219:            .put("embedding_model_id", "miewid")
src/test/java/org/ecocean/ia/MlServiceClientTest.java:228:            .put("embedding_model_id", "miewid")
src/test/java/org/ecocean/ia/MlServiceProcessorGateTest.java:63:            .put("method", "miewid-msv4.1").put("version", "4.1"));
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:41: *       array, per-result {@code embedding} / {@code embedding_model_id} /
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:78:     *  ml-service deployment uses different model_ids. */
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:154:            .put("api_endpoint", baseUrl())
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:155:            .put("predict_model_id", predictModel())
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:156:            .put("classify_model_id", classifyModel())
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:157:            .put("extract_model_id", extractModel());
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:200:            String modelId = r.optString("embedding_model_id", "");
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:203:                "results[" + i + "].embedding_model_id missing or empty");
src/test/java/org/ecocean/ia/MlServiceLiveIntegrationTest.java:250:            response.optString("embedding_model_id", "?") + "/" +

 succeeded in 1025ms:
src/main/webapp/iaResults.jsp:727:			res.status._response.response.json_result.query_config_dict.pipeline_root);
src/main/webapp/iaResults.jsp:793:		var algo_name = json_result['query_config_dict']['pipeline_root'];
src/main/webapp/iaResults.jsp:811:		var sorted = score_sort(json_result['cm_dict'][qannotId], json_result['query_config_dict']['pipeline_root']);
src/main/webapp/import.jsp:79:          newTaskParams.put("ibeis.identification", ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
src/main/webapp/import.jsp:80:          task.setParameters(newTaskParams);
src/main/webapp/import.jsp:555:	                            	if(task.getParent()!=null && task.getParent().getChildren().size()==1 && task.getParameters()!=null && task.getParameters().has("ibeis.identification")){
src/main/java/org/ecocean/ia/Task.java:56:        this.setParameters(p.getParameters());
src/main/java/org/ecocean/ia/Task.java:132:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
src/main/java/org/ecocean/ia/Task.java:139:        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
src/main/java/org/ecocean/ia/Task.java:424:    public void setParameters(String s) { // best be json, yo
src/main/java/org/ecocean/ia/Task.java:428:    public void setParameters(JSONObject j) {
src/main/java/org/ecocean/ia/Task.java:437:    public void setParameters(String key, Object value) {
src/main/java/org/ecocean/ia/Task.java:445:    public void addParameter(String key, Object value) {
src/main/java/org/ecocean/ia/Task.java:746:        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
src/main/java/org/ecocean/ia/Task.java:749:        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
src/main/java/org/ecocean/ia/Task.java:751:            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
src/main/java/org/ecocean/ia/Task.java:752:                "model_id", null);
src/main/java/org/ecocean/ia/Task.java:763:        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:766:            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
src/main/java/org/ecocean/ia/Task.java:768:        // we set HotSpotter if pipeline_root is not set here
src/main/java/org/ecocean/ia/Task.java:769:        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
src/main/java/org/ecocean/ia/Task.java:771:            getParameters().getJSONObject("ibeis.identification").optString("description",
src/main/java/org/ecocean/ia/Task.java:772:            "unknown algorithm/method"));
src/main/java/org/ecocean/ia/Task.java:841:            rtn.put("method", methodInfo);
src/main/java/org/ecocean/identity/IBEISIA.java:328:        // query_config_dict={'pipeline_root' : 'BC_DTW'}
src/main/java/org/ecocean/identity/IBEISIA.java:1515:                    subParentTask.setParameters(taskParameters);
src/main/java/org/ecocean/identity/IBEISIA.java:1720:                    embedTask.setParameters(params);
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:89:        t.setParameters(p);
src/main/java/org/ecocean/ia/plugin/TestPlugin.java:105:        t.setParameters(p);
src/main/java/org/ecocean/ia/MatchResult.java:358:        payload.put("model_id", "miewid-msv4.1");
src/main/java/org/ecocean/ia/MatchResult.java:505:            urlStr = confs.get(0).optString("api_endpoint", null);
src/main/java/org/ecocean/ia/MatchInspectionPairxEnricher.java:200:        payload.put("model_id", "miewid-msv4.1");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:194:            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:196:                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:199:                    "_mlservice_conf missing predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:214:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:275:                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:332:                String predictModelId = result.optString("predict_model_id",
src/main/java/org/ecocean/ia/MlServiceProcessor.java:333:                    det.mlConfig.optString("predict_model_id", null));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:376:                Embedding emb = new Embedding(ann, result.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:419:            Embedding emb = new Embedding(ann, response.getString("embedding_model_id"),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:521:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:687:                    && "vector".equals(entry.optString("pipeline_root", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:695:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:713:                    config.put("method", emb.getMethod());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:715:                    config.put("pipeline_root", "vector");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:730:            if (!Util.stringExists(config.optString("method", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:731:                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:732:                config.put("method", embeddingSource.optString("embedding_model_id"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:739:        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
src/main/java/org/ecocean/ia/MlServiceProcessor.java:740:            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:741:            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:898:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:34: * entirely (legacy api_endpoint-only configs that can't derive a
src/main/java/org/ecocean/ia/IAUtils.java:29:                parentTask.setParameters(tp);
src/main/java/org/ecocean/ia/MlServiceClient.java:74:     * @param config      a single {@code _mlservice_conf} entry from IA.json
src/main/java/org/ecocean/ia/MlServiceClient.java:121:            if (config.has("predict_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:122:                p.put("predict_model_id", config.opt("predict_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:123:            if (config.has("classify_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:124:                p.put("classify_model_id", config.opt("classify_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:125:            if (config.has("extract_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:126:                p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:127:            if (config.has("orientation_model_id"))
src/main/java/org/ecocean/ia/MlServiceClient.java:128:                p.put("orientation_model_id", config.opt("orientation_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:137:        if (config != null && config.has("extract_model_id")) {
src/main/java/org/ecocean/ia/MlServiceClient.java:138:            p.put("extract_model_id", config.opt("extract_model_id"));
src/main/java/org/ecocean/ia/MlServiceClient.java:229:        String modelId = parent.optString("embedding_model_id", null);
src/main/java/org/ecocean/ia/MlServiceClient.java:233:                context + " missing embedding_model_id or embedding_model_version",
src/main/java/org/ecocean/ia/MlServiceClient.java:247:            throw new IAException("INVALID", "malformed api_endpoint: " + url, false, false);
src/main/java/org/ecocean/ia/MLService.java:56:    // there can be multiple configs (differing model_id)
src/main/java/org/ecocean/ia/MLService.java:65:        Object mlc = iac.get(taxonomyString + "._mlservice_conf");
src/main/java/org/ecocean/ia/MLService.java:227:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
src/main/java/org/ecocean/ia/MLService.java:305:            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
src/main/java/org/ecocean/ia/MLService.java:326:        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
src/main/java/org/ecocean/ia/MLService.java:352:        mv[0] = conf.optString("model_id", null);
src/main/java/org/ecocean/ia/MLService.java:364:        if (endpoint == null) throw new IAException("null api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:369:            throw new IAException("api_endpoint url error: " + urlEx);
src/main/java/org/ecocean/ia/MLService.java:402:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MLService.java:418:        payload.remove("api_endpoint");
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:104:        // (Embedding.java:349-355): _id_conf.method/version first,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:105:        // then MLService.getMethodValues for legacy api_endpoint
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:118:            : matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:105:     * @param matchConfig          the ml-service _id_conf entry,
src/main/java/org/ecocean/ia/IA.java:149:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:160:        // If the species' _id_conf.default.pipeline_root is "vector" AND
src/main/java/org/ecocean/ia/IA.java:161:        // _mlservice_conf is configured, route per-asset through the
src/main/java/org/ecocean/ia/IA.java:163:        // WBIA path below — production deployments without _mlservice_conf
src/main/java/org/ecocean/ia/IA.java:186:            task.setParameters(topTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:189:            task.addParameter("detectArgs", detectArgs);
src/main/java/org/ecocean/ia/IA.java:192:            task.addParameter("__detect_url", detectionUrl);
src/main/java/org/ecocean/ia/IA.java:384:        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
src/main/java/org/ecocean/ia/IA.java:441:                newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/ia/IA.java:443:                topTask.setParameters(newTaskParams);
src/main/java/org/ecocean/ia/IA.java:449:                    newTaskParams.put("ibeis.identification",
src/main/java/org/ecocean/ia/IA.java:451:                    t.setParameters(newTaskParams);
src/main/java/org/ecocean/ia/IA.java:537:            if (fastlane) topTask.addParameter("fastlane", true);
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:86:				if(params.optJSONObject("ibeis.identification")!=null && params.optJSONObject("ibeis.identification").optJSONObject("query_config_dict")!=null){
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:87:					algo = params.optJSONObject("ibeis.identification").optJSONObject("query_config_dict").optString("pipeline_root");
src/main/webapp/appadmin/wildbookIAQueueStats.jsp:97:						if(queryConfigDict!=null && !queryConfigDict.optString("pipeline_root").equals(""))algo=queryConfigDict.optString("pipeline_root");
src/main/java/org/ecocean/servlet/DispatchServlet.java:154:            throw new DelegateNotFoundException("Couldn't access method", iax);
src/main/webapp/appadmin/resendBulkImportID.jsp:63:            parentTask.setParameters(taskParameters);
src/main/webapp/appadmin/resendBulkImportID.jsp:76:                subParentTask.setParameters(taskParameters);
src/main/webapp/appadmin/detwitter.jsp:65:    ma.setParameters(params);
src/main/java/org/ecocean/servlet/IAGateway.java:127:                task.setParameters(tparams);
src/main/java/org/ecocean/servlet/IAGateway.java:227:                task.addParameter("ibeis.detection", true);
src/main/java/org/ecocean/servlet/IAGateway.java:345:            parentTask.setParameters((String)null); // reset this, kids inherit params
src/main/java/org/ecocean/servlet/IAGateway.java:348:                newTask.setParameters(params);
src/main/java/org/ecocean/servlet/IAGateway.java:887:            parentTask.setParameters(taskParameters);
src/main/java/org/ecocean/servlet/IAGateway.java:901:        task.setParameters(taskParameters);
src/main/java/org/ecocean/servlet/EncounterForm.java:900:                            parentTask.setParameters(tp);
src/main/java/org/ecocean/servlet/GetCurrentIAInfo.java:129:                        task.getParameters().has("ibeis.identification")) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:207:    public void setParameters(String s) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:211:    public void setParameters(JSONObject j) {
src/main/java/org/ecocean/servlet/importer/ImportTask.java:432:                        atask.getParameters().has("ibeis.identification")) {
src/main/java/org/ecocean/servlet/LightRestServlet.java:628:                " does not have decorateJson() method");
src/main/java/org/ecocean/servlet/LightRestServlet.java:646:// System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");
src/main/java/org/ecocean/servlet/MediaAssetCreate.java:123:                        parentTask.setParameters(tp);
src/main/java/org/ecocean/servlet/ProjectIA.java:87:                            parentTask.setParameters(tp);
src/main/java/org/ecocean/servlet/RestServlet.java:748:                // System.out.println("i guess " + obj.getClass() + " does not have decorateJson() method");
src/main/java/org/ecocean/servlet/RestServlet.java:770:            // System.out.println("i guess " + obj.getClass() + " does not have sanitizeJson() method");

exec
/bin/bash -lc "sed -n '480,535p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '420,465p' src/main/java/org/ecocean/ia/IA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '60,90p' src/main/webapp/import.jsp" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1495,1525p' src/main/java/org/ecocean/identity/IBEISIA.java && sed -n '1710,1730p' src/main/java/org/ecocean/identity/IBEISIA.java" in /mnt/c/Wildbook-clean2
 succeeded in 311ms:
            if (parentTask != null && parentTask.getParameters() != null) {
                newTaskParams = parentTask.getParameters();
                System.out.println("newTaskParams: " + newTaskParams.toString());
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
            }
            if ((opts == null) || (opts.size() < 1)) continue; // no ID for this iaClass.
            // just one IA class, one algorithm case
            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
                newTaskParams.put("ibeis.identification",
                    ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
                topTask.setParameters(newTaskParams);
                tasks.add(topTask); // topTask will be used as *the*(only) task -- no children
            } else {
                for (int i = 0; i < opts.size(); i++) {
                    Task t = new Task();
                    t.setObjectAnnotations(annsOneIAClass);
                    newTaskParams.put("ibeis.identification",
                        ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));                                        // overwrites each time
                    t.setParameters(newTaskParams);
                    topTask.addChild(t);
                    tasks.add(t);
                }
            }
            newTaskParams.put("fastlane", fastlane);
            if (fastlane) newTaskParams.put("lane", "fast");
            myShepherd.storeNewTask(topTask);

            // these are re-used in every task
            JSONArray annArr = new JSONArray();
            for (Annotation ann : annsOneIAClass) {
                annArr.put(ann.getId());
            }
            JSONObject aj = new JSONObject();

 succeeded in 321ms:
        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
        // Carry forward attempt + firstDeferredAt so age-out is
        // measured by elapsed wall-clock from the original DEFER, not
        // by attempt count (Codex round-4 OQ #1).
        int attempt = jobData.optInt("attempt", 2);
        Long firstDeferredAt = jobData.has("firstDeferredAt")
            ? Long.valueOf(jobData.optLong("firstDeferredAt")) : null;
        // Re-gate; deferred match earns the same protection as the
        // initial call (Codex round-2 Major: don't degrade back to
        // today's bug on the first deferral).
        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
            attempt, firstDeferredAt);
    }

    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
        JSONObject matchConfig) {
        if (annotationIds == null || annotationIds.isEmpty()) {
            markTaskCompleted(taskId);
            return MlServiceJobOutcome.ok(new ArrayList<String>());
        }

        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "runMatchProspects");
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
            Task matchTask = (parent == null) ? new Task() : new Task(parent);
            matchTask.setObjectAnnotations(anns);
            matchTask.addParameter("mlServiceV2Match", true);
            shep.getPM().makePersistent(matchTask);
            // findMatchProspects returns false when the match config is not
            // a vector config or matchConfig is null. Don't leave the match
            // task without a terminal status — mark the parent task error.
            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
            if (!ran) {
                matchTask.setStatus("error");
                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
                    "findMatchProspects rejected match config: " +
                    (matchConfig == null ? "null" : matchConfig.toString()));
                matchTask.setCompletionDateInMilliseconds();
                // Update the parent task in this same transaction (parent is
                // already loaded above) so the two updates commit atomically.
                // Splitting across transactions risks leaving the parent

 succeeded in 332ms:
            for(int y=0;y<maLength;y++) {
              newOpts.add(matchingAlgorithms.getJSONObject(y));
            }
            System.out.println("matchingAlgorithms2: "+newOpts.toString());
            if(newOpts.size()>0) {
              opts=newOpts;
              System.out.println("Swapping opts for newOpts!!");
            }


          }
 
        if ((opts == null) || (opts.size() < 1)) {
        	System.out.println("returning opts=null");
        	return;
        }  // no ID for this iaClass.

        // just one IA class, one algorithm case
        
          newTaskParams.put("ibeis.identification", ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
          task.setParameters(newTaskParams);

        boolean fastlane=task.isFastlane(myShepherd);
        newTaskParams.put("fastlane", fastlane);
        if(fastlane)newTaskParams.put("lane", "fast");

        //these are re-used in every task
        JSONArray annArr = new JSONArray();
        annArr.put(task.getObjectAnnotations().get(0).getId());
        JSONObject aj = new JSONObject();
        aj.put("annotationIds", annArr);

 succeeded in 322ms:
                            ArrayList<Annotation> annots = new ArrayList<Annotation>();
                            annots.add(annot);
                            needIdentifyingMap.put(enc.getCatalogNumber(), annots);
                        }
                    }
                }
                // send to ID by Encounter
                for (String encUUID : needIdentifyingMap.keySet()) {
                    ArrayList<Annotation> annots = needIdentifyingMap.get(encUUID);
                    JSONObject taskParameters = new JSONObject();
                    JSONObject mf = new JSONObject();
                    Encounter enc = myShepherd2.getEncounter(encUUID);
                    if (enc != null && enc.getLocationID() != null) {
                        ArrayList<String> locationIDs = new ArrayList<String>();
                        List<String> matchTheseLocationIDs = LocationID.getIDForParentAndChildren(
                            enc.getLocationID(), locationIDs, null);
                        mf.put("locationIds", matchTheseLocationIDs);
                    }
                    taskParameters.put("matchingSetFilter", mf);
                    Task subParentTask = new Task();
                    subParentTask.setParameters(taskParameters);
                    myShepherd2.storeNewTask(subParentTask);
                    myShepherd2.updateDBTransaction();

                    Task childTask = IA.intakeAnnotations(myShepherd2, annots, subParentTask,
                        false);
                    myShepherd2.storeNewTask(childTask);
                    myShepherd2.updateDBTransaction();
                    subParentTask.addChild(childTask);
                    myShepherd2.updateDBTransaction();
                }
                for (Annotation ann : allAnns) {
                    ann.setIdentificationStatus(STATUS_PROCESSING_MLSERVICE);
                }
                myShepherd.updateDBTransaction();
                // this will queue up annots to have embeddings extracted and set on annot
                if (allAnns.size() > 0) {
                    Task embedTask = new Task(task); // this should copy task's parameters
                    JSONObject params = embedTask.getParameters(); // but we need to modify them
                    params.remove("ibeis.detection");
                    params.put("embeddingExtraction", true);
                    embedTask.setParameters(params);
                    embedTask.setObjectAnnotations(allAnns);
                    embedTask.setStatus("initiated");
                    myShepherd.getPM().makePersistent(embedTask);
                    myShepherd.updateDBTransaction();
                    for (Annotation ann : allAnns) {
                        ann.queueForEmbeddingExtraction(embedTask, myShepherd);
                    }
                }
                if (amap.length() > 0) rtn.put("annotations", amap); // needed to kick off ident jobs with return value


exec
/bin/bash -lc "sed -n '320,395p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '150,210p' src/main/java/org/ecocean/ia/IA.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"runMatchProspects|waitAndRunMatch|mlServiceV2Match|matchConfig\" src/main/java/org/ecocean/ia src/main/java/org/ecocean/identity src/main/java/org/ecocean/servlet -g'*.java'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '20,60p' src/main/java/org/ecocean/ia/IAUtils.java" in /mnt/c/Wildbook-clean2
 succeeded in 271ms:
            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
                useClauses);
            // getMatchingSetQuery can return null (e.g. encounter missing,
            // taxonomy filtered out). Skip this subtask cleanly rather than
            // NPE'ing on the queryCount/getMatchQuery below.
            if (matchingSetQuery == null) {
                System.out.println("findMatchProspects() null matchingSetQuery for " + ann);
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("REQUIRED", "null matchingSetQuery");
                subTask.setCompletionDateInMilliseconds();
                myShepherd.getPM().makePersistent(subTask);
                subtasksFailed++;
                continue;
            }
            // Count eligible candidates BEFORE getMatchQuery, which mutates
            // matchingSetQuery in place (Annotation.java:1203). The previous
            // order returned the knn result count instead of the candidate
            // count. Defense in depth: also pass a deep clone of
            // matchingSetQuery to getMatchQuery so the eligible-set object
            // never silently changes shape if a future caller relies on it.
            OpenSearch os = new OpenSearch();
            int numberCandidates = -2;
            try {
                numberCandidates = os.queryCount("annotation", matchingSetQuery);
            } catch (IOException ex) {
                System.out.println("findMatchProspects() numCandidates query failed with " + ex);
            }
            // Read method/version directly from the new _id_conf contract;
            // fall back to splitting model_id for legacy configs.
            String method = iaConfig.optString("method", null);
            String version = iaConfig.optString("version", null);
            if (!Util.stringExists(method)) {
                String[] mv = MLService.getMethodValues(iaConfig);
                method = mv[0];
                version = mv[1];
            }
            JSONObject matchQuery = ann.getMatchQuery(method, version,
                new JSONObject(matchingSetQuery.toString()));
            // i think this will never happen now, due to on-the-fly fix above; but leaving to be safe
            if (matchQuery == null) {
                System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                    " due to no suitable embeddings for " + iaConfig);
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("REQUIRED",
                    "no suitable embeddings for getMatches()");
                subTask.setCompletionDateInMilliseconds();
                myShepherd.getPM().makePersistent(subTask);
                subtasksFailed++;
                continue; // on to next ann
            }
            List<Annotation> prospects = ann.getMatches(myShepherd, matchQuery);
            System.out.println("findMatchProspects() on " + ann + " found " +
                Util.collectionSize(prospects) + " prospects (in " + numberCandidates +
                " candidates) for subTask " + subTask.getId());
            boolean mrOk = true;
            try {
                // we build this even if empty, cuz that means we got results; just not nice ones
                MatchResult mr = new MatchResult(subTask, prospects, numberCandidates, myShepherd);
                System.out.println("findMatchProspects() created " + mr + " on " + subTask);
                myShepherd.getPM().makePersistent(mr);
            } catch (IOException ex) {
                System.out.println("findMatchProspects() MatchResult creation failed on " +
                    subTask + ": " + ex);
                ex.printStackTrace();
                mrOk = false;
            }
            if (mrOk) {
                subTask.setStatus("completed");
                subtasksOk++;
            } else {
                subTask.setStatus("error");
                subTask.setStatusDetailsAddError("UNKNOWN",
                    "MatchResult persistence failed");
                subtasksFailed++;
            }
            subTask.setCompletionDateInMilliseconds();

 succeeded in 273ms:
        topTask.setObjectMediaAssets(mas);
        myShepherd.storeNewTask(topTask);

        String context = myShepherd.getContext();
        String baseUrl = getBaseURL(context);

        // Ia configs are keyed off taxonomies
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();

        // Migration plan v2 §commit #10b: routing reroute.
        // If the species' _id_conf.default.pipeline_root is "vector" AND
        // _mlservice_conf is configured, route per-asset through the
        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
        // WBIA path below — production deployments without _mlservice_conf
        // see no behavior change at all.
        //
        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
        // child finalization is local; no first-finisher-wins. The topTask
        // remains as the aggregator for the caller contract (and so legacy
        // summary code that reads topTask.objectMediaAssets keeps working).
        if (iaConfig != null && taxy != null &&
            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
                context, baseUrl);
        }

        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONObject dj = new JSONObject();
        dj.put("mediaAssetIds", maArr);
        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
        Boolean[] sent = new Boolean[numDetectAlgos];
        for (int i = 0; i < numDetectAlgos; i++) {
            // task for this job (only create new (child) tasks if multiple detect algos)
            Task task = (numDetectAlgos == 1) ? topTask : new Task();
            task.setObjectMediaAssets(mas);
            task.setParameters(topTask.getParameters());

            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
            task.addParameter("detectArgs", detectArgs);

            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
            task.addParameter("__detect_url", detectionUrl);

            JSONObject qjob = new JSONObject();
            qjob.put("detect", dj);
            qjob.put("__detect_args", detectArgs);
            qjob.put("__detect_url", detectionUrl);
            // task is queued here
            qjob.put("taskId", topTask.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
            if (tweetAssetId != -1) {
                qjob.put("tweetAssetId", tweetAssetId);
            }
            System.out.println(
                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
            sent[i] = false;
            try {
                // job is queued here

 succeeded in 283ms:
                ma.setDetectionStatus(IBEISIA.STATUS_INITIATED);
            }
            Task parentTask = null; // this is *not* persisted, but only used so intakeMediaAssets will inherit its params
            if (enc.getLocationID() != null) {
                parentTask = new Task();
                JSONObject tp = new JSONObject();
                JSONObject mf = new JSONObject();
                mf.put("locationId", enc.getLocationID());
                tp.put("matchingSetFilter", mf);
                parentTask.setParameters(tp);
            }
            Task task = org.ecocean.ia.IA.intakeMediaAssets(myShepherd, enc.getMedia(), parentTask); // are they *really* persisted for another thread (queue)
            myShepherd.storeNewTask(task);
            taskIDs.add(task.getId());
        }
        return taskIDs;
    }
}

 succeeded in 717ms:
src/main/java/org/ecocean/ia/MatchEligibilityQuery.java:19: * the matchConfig provides them) have an EMBEDDING row for the
src/main/java/org/ecocean/ia/MlServiceProcessor.java:124:        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
src/main/java/org/ecocean/ia/MlServiceProcessor.java:126:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:127:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:160:        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:161:        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:162:            matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:215:                configs.matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:250:            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:276:                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:410:            JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:411:            if (hasEmbeddingForMatchConfig(ann, matchConfig)) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:435:    private MlServiceJobOutcome waitAndRunMatch(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:436:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:440:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig, 1, null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:444:     * Shared body for the initial {@link #waitAndRunMatch} call and
src/main/java/org/ecocean/ia/MlServiceProcessor.java:453:    private MlServiceJobOutcome waitAndRunMatchInternal(List<String> annotationIds,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:454:        String taskId, JSONObject matchConfig, int attempt, Long firstDeferredAt) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:456:            annotationIds, taskId, matchConfig, attempt, firstDeferredAt);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:459:            return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:461:            enqueueDeferredMatch(annotationIds, taskId, matchConfig, gate);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:470:            return runMatchProspects(annotationIds, taskId, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:480:        JSONObject matchConfig = jobData.optJSONObject("matchConfig");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:481:        if (matchConfig == null) matchConfig = inferMatchConfig(annotationIds);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:491:        return waitAndRunMatchInternal(annotationIds, taskId, matchConfig,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:495:    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:496:        JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:503:        shep.setAction(ACTION_PREFIX + "runMatchProspects");
src/main/java/org/ecocean/ia/MlServiceProcessor.java:521:            matchTask.addParameter("mlServiceV2Match", true);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:524:            // a vector config or matchConfig is null. Don't leave the match
src/main/java/org/ecocean/ia/MlServiceProcessor.java:526:            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:531:                    (matchConfig == null ? "null" : matchConfig.toString()));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:567:     * after the main runMatchProspects transaction has closed, so the
src/main/java/org/ecocean/ia/MlServiceProcessor.java:674:        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:675:        return new ConfigPair(mlConfig, matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:680:        JSONObject matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:688:                    matchConfig = new JSONObject(entry.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:693:        if (matchConfig == null) matchConfig = new JSONObject();
src/main/java/org/ecocean/ia/MlServiceProcessor.java:694:        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
src/main/java/org/ecocean/ia/MlServiceProcessor.java:696:            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
src/main/java/org/ecocean/ia/MlServiceProcessor.java:698:        return matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:725:    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
src/main/java/org/ecocean/ia/MlServiceProcessor.java:727:        JSONObject config = (matchConfig == null) ? new JSONObject()
src/main/java/org/ecocean/ia/MlServiceProcessor.java:728:            : new JSONObject(matchConfig.toString());
src/main/java/org/ecocean/ia/MlServiceProcessor.java:824:        JSONObject matchConfig, MatchVisibilityGate.GateOutcome gate) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:835:        if (matchConfig != null) payload.put("matchConfig", matchConfig);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:895:    private static boolean hasEmbeddingForMatchConfig(Annotation ann, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:897:        if (matchConfig == null) return ann.numberEmbeddings() > 0;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:898:        String method = matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:899:        String version = matchConfig.optString("version", null);
src/main/java/org/ecocean/ia/MlServiceProcessor.java:911:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:913:        ConfigPair(JSONObject mlConfig, JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:915:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:923:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:927:            JSONObject matchConfig) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:931:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:939:            this.matchConfig = null;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:952:        final JSONObject matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:958:            JSONObject matchConfig, double[] bbox, double theta) {
src/main/java/org/ecocean/ia/MlServiceProcessor.java:962:            this.matchConfig = matchConfig;
src/main/java/org/ecocean/ia/MlServiceProcessor.java:972:            this.matchConfig = null;
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:105:     * @param matchConfig          the ml-service _id_conf entry,
src/main/java/org/ecocean/ia/MatchVisibilityGate.java:119:        JSONObject matchConfig,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:82:        JSONObject matchConfig,
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:102:        // Derive method/methodVersion from matchConfig using the same
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:117:        String method = (matchConfig == null) ? null
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:118:            : matchConfig.optString("method", null);
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:119:        String methodVersion = (matchConfig == null) ? null
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:120:            : matchConfig.optString("version", null);
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:121:        if (!Util.stringExists(method) && matchConfig != null) {
src/main/java/org/ecocean/ia/MatchVisibilityGateImpl.java:122:            String[] mv = MLService.getMethodValues(matchConfig);

exec
/bin/bash -lc "sed -n '280,325p' src/main/java/org/ecocean/Embedding.java" in /mnt/c/Wildbook-clean2
 succeeded in 291ms:
        }
        System.out.println("findMatchProspects() (task " + task.getId() + ", " +
            task.numberAnnotations() + " annots) has embedding match: " + iaConfig);
        // Track per-subtask outcomes so the parent task's terminal state
        // reflects reality (plan v2 §commit #3: previous code unconditionally
        // marked the parent "completed" even if every subtask failed).
        int subtasksOk = 0;
        int subtasksFailed = 0;
        for (Annotation ann : task.getObjectAnnotations()) {
            // every ann gets a subTask
            Task subTask = new Task(task);
            subTask.addObject(ann);
            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
            if (ann.numberEmbeddings() < 1) {
                System.out.println(
                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
                    ann);
                try {
                    ann.extractEmbeddings(myShepherd);
                } catch (IAException ex) {
                    System.out.println(
                        "[WARNING] findMatchProspects() unable to extractEmbeddings on " + ann +
                        " due to: " + ex);
                }
                // if none now, we just fail and continue onto next annot
                if (ann.numberEmbeddings() < 1) {
                    System.out.println("findMatchProspects() cannot getMatches() on " + ann +
                        " due to no suitable embeddings for " + iaConfig);
                    subTask.setStatus("error");
                    subTask.setStatusDetailsAddError("REQUIRED",
                        "no suitable embeddings for getMatches()");
                    subTask.setCompletionDateInMilliseconds();
                    myShepherd.getPM().makePersistent(subTask);
                    subtasksFailed++;
                    continue;
                }
            }
            // Build matchingSetQuery for the candidate count.
            boolean useClauses = false; // TODO how??
            JSONObject matchingSetQuery = ann.getMatchingSetQuery(myShepherd, task.getParameters(),
                useClauses);
            // getMatchingSetQuery can return null (e.g. encounter missing,
            // taxonomy filtered out). Skip this subtask cleanly rather than
            // NPE'ing on the queryCount/getMatchQuery below.
            if (matchingSetQuery == null) {

exec
/bin/bash -lc "sed -n '170,290p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '90,170p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '60,105p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '650,745p' src/main/java/org/ecocean/ia/MlServiceProcessor.java" in /mnt/c/Wildbook-clean2
 succeeded in 272ms:
        shep.setAction(ACTION_PREFIX + "loadDetectionContext");
        try {
            FeatureType.initAll(shep);
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Encounter enc = Util.stringExists(encounterId) ? shep.getEncounter(encounterId) : null;
            Task task = Task.load(taskId, shep);

            String staleReason = detectionStaleReason(ma, enc, encounterId);
            if (staleReason != null) {
                markTaskDroppedStale(shep, task, staleReason);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale(staleReason));
            }

            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString, enc);
            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
            if (configs == null) {
                ma.setDetectionStatus(IBEISIA.STATUS_PENDING_SPECIES);
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.stale("pending-species"));
            }

            if (!Util.stringExists(configs.mlConfig.optString("predict_model_id", null))) {
                markTaskError(task, "INVALID",
                    "_mlservice_conf missing predict_model_id for " + effectiveTaxonomy);
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError("INVALID",
                    "_mlservice_conf missing predict_model_id"));
            }

            URL webUrl = ma.webURL();
            if (webUrl == null) {
                markTaskError(task, "INVALID_IMAGE_URI",
                    "MediaAsset " + maId + " has no webURL");
                shep.commitDBTransaction();
                return DetectionContext.done(MlServiceJobOutcome.validationError(
                    "INVALID_IMAGE_URI", "MediaAsset " + maId + " has no webURL"));
            }

            ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new DetectionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig);
        } finally {
            shep.rollbackAndClose();
        }
    }

    private ExtractionContext loadExtractionContext(String taxonomyString, String taskId,
        String annId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "loadExtractionContext");
        try {
            shep.beginDBTransaction();
            Annotation ann = shep.getAnnotation(annId);
            Task task = Task.load(taskId, shep);
            if (ann == null) {
                markTaskDroppedStale(shep, task, "annotation missing");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("annotation missing"));
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) {
                markTaskDroppedStale(shep, task, "annotation media asset missing");
                shep.commitDBTransaction();
                return ExtractionContext.done(
                    MlServiceJobOutcome.stale("annotation media asset missing"));
            }

            String effectiveTaxonomy = effectiveTaxonomyString(taxonomyString,
                ann.findEncounter(shep));
            ConfigPair configs = activeConfigs(shep, effectiveTaxonomy);
            if (configs == null) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("pending-species"));
            }
            if (hasEmbeddingForMatchConfig(ann, configs.matchConfig)) {
                markTaskCompleted(task);
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.stale("embedding-exists"));
            }

            URL webUrl = ma.webURL();
            if (webUrl == null) {
                markTaskError(task, "INVALID_IMAGE_URI",
                    "Annotation " + annId + " media asset has no webURL");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.validationError(
                    "INVALID_IMAGE_URI", "Annotation " + annId + " media asset has no webURL"));
            }
            int[] bbox = ann.getBbox();
            if (bbox == null || bbox.length != 4) {
                markTaskError(task, "INVALID_BBOX", "Annotation " + annId + " has no bbox");
                shep.commitDBTransaction();
                return ExtractionContext.done(MlServiceJobOutcome.validationError("INVALID_BBOX",
                    "Annotation " + annId + " has no bbox"));
            }

            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING_MLSERVICE);
            shep.commitDBTransaction();
            return new ExtractionContext(webUrl.toString(),
                configs.mlConfig.optString("api_endpoint", null), configs.mlConfig,
                configs.matchConfig, toDoubleArray(bbox), ann.getTheta());
        } finally {
            shep.rollbackAndClose();
        }
    }

    private MlServiceJobOutcome finalizeZeroDetections(String maId, String taskId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "finalizeZeroDetections");
        try {
            shep.beginDBTransaction();
            MediaAsset ma = shep.getMediaAsset(maId);
            Task task = Task.load(taskId, shep);
            String staleReason = detectionStaleReason(ma, null, null);
            if (staleReason != null) {

 succeeded in 280ms:
    }

    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
        String taskId, String encounterId, String maId) {
        DetectionContext det = null;

        try {
            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (det.outcome != null) return det.outcome;

        JSONObject response;
        try {
            response = client.pipeline(det.apiEndpoint, det.imageUri, det.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return finalizeZeroDetections(maId, taskId);
        }

        PersistResult persisted = persistDetections(maId, encounterId, taskId, det, results);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(det.matchConfig, results.optJSONObject(0),
            det.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private MlServiceJobOutcome processExtraction(JSONObject jobData, String taxonomyString,
        String taskId, String annId) {
        ExtractionContext ext = null;

        try {
            ext = loadExtractionContext(taxonomyString, taskId, annId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (ext.outcome != null) return ext.outcome;

        JSONObject response;
        try {
            response = client.extract(ext.apiEndpoint, ext.imageUri, ext.bbox, ext.theta,
                ext.mlConfig);
        } catch (IAException ex) {
            if (ex.shouldRequeue()) {
                IAGateway.requeueJob(jobData, ex.shouldIncrement());
                return MlServiceJobOutcome.requeue();
            }
            markTaskError(taskId, ex.getCode(), ex.getMessage());
            return mapNonRetryableError(ex);
        }

        PersistResult persisted = persistExtraction(annId, taskId, ext, response);
        if (persisted.outcome != null) return persisted.outcome;

        JSONObject matchConfig = ensureMatchConfig(ext.matchConfig, response, ext.mlConfig);
        MlServiceJobOutcome matchOutcome = waitAndRunMatch(persisted.annotationIds, taskId,
            matchConfig);
        if (matchOutcome != null) return matchOutcome;
        return MlServiceJobOutcome.ok(persisted.annotationIds);
    }

    private DetectionContext loadDetectionContext(String taxonomyString, String taskId,
        String encounterId, String maId) {
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "loadDetectionContext");

 succeeded in 291ms:
        DeferredMatchPublisher deferredPublisher) {
        this.context = context;
        this.client = client;
        this.visibilityGate = visibilityGate;
        this.deferredPublisher = deferredPublisher;
    }

    /** Process one ml-service queue job. Returns the outcome. */
    public MlServiceJobOutcome process(JSONObject jobData) {
        if (jobData == null) {
            return MlServiceJobOutcome.validationError("INVALID_PAYLOAD", "payload is null");
        }
        if (jobData.optBoolean("deferredMatch", false)) {
            return runDeferredMatch(jobData);
        }

        String taxonomyString = jobData.optString("taxonomyString", null);
        String taskId = jobData.optString("taskId", null);
        String encounterId = jobData.optString("encounterId", null);

        if (jobData.has("mediaAssetId")) {
            String maId = String.valueOf(jobData.opt("mediaAssetId"));
            return processDetection(jobData, taxonomyString, taskId, encounterId, maId);
        }
        if (jobData.has("annotationId")) {
            String annId = jobData.optString("annotationId", null);
            return processExtraction(jobData, taxonomyString, taskId, annId);
        }
        return MlServiceJobOutcome.validationError("INVALID_PAYLOAD",
            "neither mediaAssetId nor annotationId in payload");
    }

    private MlServiceJobOutcome processDetection(JSONObject jobData, String taxonomyString,
        String taskId, String encounterId, String maId) {
        DetectionContext det = null;

        try {
            det = loadDetectionContext(taxonomyString, taskId, encounterId, maId);
        } catch (Exception ex) {
            markTaskError(taskId, "PERSIST", "load/revalidate failed: " + ex.getMessage());
            return MlServiceJobOutcome.persistError("PERSIST", ex.getMessage());
        }
        if (det.outcome != null) return det.outcome;

        JSONObject response;
        try {

 succeeded in 261ms:
    }

    static Annotation findExistingAnnotation(MediaAsset ma, String predictModelId,
        String bboxKey, String thetaKey) {
        if (ma == null) return null;
        for (Annotation ann : ma.getAnnotations()) {
            if (ann == null) continue;
            if (!sameString(predictModelId, ann.getPredictModelId())) continue;
            if (!sameString(bboxKey, ann.getBboxKey())) continue;
            if (!sameString(thetaKey, ann.getThetaKey())) continue;
            return ann;
        }
        return null;
    }

    private ConfigPair activeConfigs(Shepherd shep, String taxonomyString) {
        if (!Util.stringExists(taxonomyString)) return null;
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        if (iac == null) return null;
        Taxonomy taxy = shep.getOrCreateTaxonomy(taxonomyString, false);
        JSONArray configs = iac.getActiveMlServiceConfigs(taxy);
        if (configs == null || configs.length() == 0) return null;
        JSONObject mlConfig = configs.optJSONObject(0);
        if (mlConfig == null) return null;
        JSONObject matchConfig = defaultMatchConfig(iac, taxy, mlConfig);
        return new ConfigPair(mlConfig, matchConfig);
    }

    private JSONObject defaultMatchConfig(IAJsonProperties iac, Taxonomy taxy,
        JSONObject mlConfig) {
        JSONObject matchConfig = null;
        JSONArray identConfigs = iac.getIdentConfig(taxy);
        if (identConfigs != null) {
            for (int i = 0; i < identConfigs.length(); i++) {
                JSONObject entry = identConfigs.optJSONObject(i);
                if (entry == null) continue;
                if (entry.optBoolean("default", false)
                    && "vector".equals(entry.optString("pipeline_root", null))) {
                    matchConfig = new JSONObject(entry.toString());
                    break;
                }
            }
        }
        if (matchConfig == null) matchConfig = new JSONObject();
        if (!Util.stringExists(matchConfig.optString("api_endpoint", null))
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            matchConfig.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return matchConfig;
    }

    private JSONObject inferMatchConfig(List<String> annotationIds) {
        if (annotationIds == null || annotationIds.isEmpty()) return null;
        Shepherd shep = new Shepherd(context);
        shep.setAction(ACTION_PREFIX + "inferMatchConfig");
        try {
            shep.beginDBTransaction();
            for (String annId : annotationIds) {
                Annotation ann = shep.getAnnotation(annId);
                if (ann == null) continue;
                Embedding emb = ann.getAnEmbedding();
                if (emb != null) {
                    JSONObject config = new JSONObject();
                    config.put("method", emb.getMethod());
                    config.put("version", emb.getMethodVersion());
                    config.put("pipeline_root", "vector");
                    return config;
                }
            }
            return null;
        } finally {
            shep.rollbackAndClose();
        }
    }

    private JSONObject ensureMatchConfig(JSONObject matchConfig, JSONObject embeddingSource,
        JSONObject mlConfig) {
        JSONObject config = (matchConfig == null) ? new JSONObject()
            : new JSONObject(matchConfig.toString());
        if (embeddingSource != null) {
            if (!Util.stringExists(config.optString("method", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_id", null))) {
                config.put("method", embeddingSource.optString("embedding_model_id"));
            }
            if (!Util.stringExists(config.optString("version", null))
                && Util.stringExists(embeddingSource.optString("embedding_model_version", null))) {
                config.put("version", embeddingSource.optString("embedding_model_version"));
            }
        }
        if (!Util.stringExists(config.optString("api_endpoint", null)) && mlConfig != null
            && Util.stringExists(mlConfig.optString("api_endpoint", null))) {
            config.put("api_endpoint", mlConfig.optString("api_endpoint"));
        }
        return config;
    }


exec
/bin/bash -lc 'rg -n "intakeMediaAssetsOneSpeciesMlService|queue.*MlService|mediaAssetId|annotationId" src/main/java/org/ecocean/ia/IA.java src/main/java/org/ecocean/servlet/IAGateway.java' in /mnt/c/Wildbook-clean2
 succeeded in 304ms:
src/main/java/org/ecocean/servlet/IAGateway.java:133:                if (j.optJSONArray("annotationIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:207:        if (j.optJSONArray("mediaAssetIds") != null) {
src/main/java/org/ecocean/servlet/IAGateway.java:208:            JSONArray ids = j.getJSONArray("mediaAssetIds");
src/main/java/org/ecocean/servlet/IAGateway.java:294:        JSONArray alist = j.optJSONArray("annotationIds");
src/main/java/org/ecocean/servlet/IAGateway.java:375:                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
src/main/java/org/ecocean/servlet/IAGateway.java:376:                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
src/main/java/org/ecocean/servlet/IAGateway.java:410:        taskRes.put("annotationIds", jids);
src/main/java/org/ecocean/servlet/IAGateway.java:518:    private String mediaAssetIdToUUID(int id) {
src/main/java/org/ecocean/servlet/IAGateway.java:910:        qjob.put("mediaAssetIds", maIds);
src/main/java/org/ecocean/ia/IA.java:172:            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
src/main/java/org/ecocean/ia/IA.java:178:        dj.put("mediaAssetIds", maArr);
src/main/java/org/ecocean/ia/IA.java:229:     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
src/main/java/org/ecocean/ia/IA.java:242:    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
src/main/java/org/ecocean/ia/IA.java:246:            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
src/main/java/org/ecocean/ia/IA.java:250:        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
src/main/java/org/ecocean/ia/IA.java:259:     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
src/main/java/org/ecocean/ia/IA.java:281:    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
src/main/java/org/ecocean/ia/IA.java:304:        qjob.put("mediaAssetId", ma.getId());
src/main/java/org/ecocean/ia/IA.java:316:            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
src/main/java/org/ecocean/ia/IA.java:466:            aj.put("annotationIds", annArr);
src/main/java/org/ecocean/ia/IA.java:540:            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
src/main/java/org/ecocean/ia/IA.java:557:            JSONArray alist = jin.optJSONArray("annotationIds");

exec
/bin/bash -lc "sed -n '220,325p' src/main/java/org/ecocean/ia/IA.java" in /mnt/c/Wildbook-clean2
 succeeded in 270ms:
        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
            " assets; queued? = " + sent + "; " + topTask);
        return topTask;
    }

    /**
     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
     * vector pipeline. Each MediaAsset gets its own child Task under
     * topTask; each emits a {@code mlServiceV2:true} payload to the
     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
     * picks them up via the IAGateway dispatcher (commit #10a).
     *
     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
     * topTask. The topTask itself remains as the aggregator that holds the
     * full MediaAsset list for caller-side summary code.</p>
     *
     * <p>encounterId is derived best-effort from the MediaAsset's existing
     * trivial annotation (every Encounter.addMediaAsset call creates one).
     * If null, MlServiceProcessor persists annotations without explicit
     * Encounter linkage and downstream MediaAsset.assignEncounters handles
     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
     */
    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
        int queued = 0;
        for (MediaAsset ma : mas) {
            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
                queued++;
            }
        }
        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
        return topTask;
    }

    /**
     * Build and enqueue one v2 ml-service job for a single MediaAsset.
     * Returns {@code true} iff the FileQueue write succeeded.
     *
     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
     * normal intake path) and the startup stale-mlservice reconciler in
     * {@code StartupWildbook}. The reconciler relies on the boolean
     * return to decide whether to commit accompanying state changes; the
     * normal intake path tolerates the swallowed-failure behavior.</p>
     *
     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
     * internally commits/reopens the transaction, so the child Task row
     * is persisted before this method enqueues. On enqueue failure the
     * child Task remains in the DB as an orphan — there is no queued
     * job that will ever drive it. The orphan IS still discoverable
     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
     * the task references the MediaAsset through objectMediaAssets),
     * so it may surface in operator-facing task listings until cleaned
     * up by an out-of-band path. Callers that need cleanup should
     * delete the orphan explicitly; the default posture here is to
     * accept it since FileQueue write failures are rare.</p>
     *
     * <p>If {@code topTask} is null a fresh root task is created inside
     * this method. This matches the reconciler's use case where there is
     * no caller-side aggregator umbrella.</p>
     */
    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
        Taxonomy taxy, Task topTask, String context, String baseUrl) {
        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
        singleton.add(ma);
        childTask.setObjectMediaAssets(singleton);
        myShepherd.storeNewTask(childTask);

        // Best-effort encounterId via existing annotations on the MA.
        String encounterId = null;
        ArrayList<Annotation> existing = ma.getAnnotations();
        if (existing != null) {
            for (Annotation a : existing) {
                Encounter enc = a.findEncounter(myShepherd);
                if (enc != null) {
                    encounterId = enc.getId();
                    break;
                }
            }
        }

        JSONObject qjob = new JSONObject();
        qjob.put("mlServiceV2", true);
        qjob.put("mediaAssetId", ma.getId());
        qjob.put("taxonomyString", taxy.getScientificName());
        qjob.put("taskId", childTask.getId());
        qjob.put("__context", context);
        qjob.put("__baseUrl", baseUrl);
        if (Util.stringExists(encounterId)) {
            qjob.put("encounterId", encounterId);
        }

        try {
            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
            return false;
        }
    }

    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
        int count = 0;
        int stopAfter = 200000;
        int batchThreshold = 50;

exec
/bin/bash -lc "sed -n '1,240p' src/test/java/org/ecocean/api/TaskMatchResults.java" in /mnt/c/Wildbook-clean2
 succeeded in 303ms:
package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.ia.Task;

import org.ecocean.api.GenericObject;
// import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskMatchResults {
    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    GenericObject apiServlet;
    StringWriter responseOut;

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new GenericObject();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);

/*
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenThrow(new RuntimeException("ohmgee"));
            })) {
            mockRequest = mock(HttpServletRequest.class);
            mockResponse = mock(HttpServletResponse.class);
            apiServlet = new SiteSettings();
        }
 */
    }

    @Test void apiGet401()
    throws ServletException, IOException {
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(401);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    // basically tests api path without /match-results
    @Test void apiGetInvalidOperation()
    throws ServletException, IOException {
        User user = mock(User.class);
        String id = "some-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(null);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(400);
                assertFalse(jout.getBoolean("success"));
                assertTrue(jout.getString("debug").contains("invalid tasks operation"));
            }
        }
    }

    @Test void apiGet404()
    throws ServletException, IOException {
        User user = mock(User.class);
        String id = "404-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(null);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(404);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    @Test void apiGetSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        // this doesnt really test the output value of "matchResultsRoot", since we
        // test task.matchResultsJson() seperately so dont care about the null value here
        Task task = mock(Task.class);
        String id = "ok-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(task);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(200);
                assertTrue(jout.getBoolean("success"));
            }
        }
    }
}

exec
/bin/bash -lc 'git ls-files --eol frontend/src/pages/MatchResultsPage/MatchResults.jsx frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js frontend/src/pages/MatchResultsPage/helperFunctions.js frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx src/main/java/org/ecocean/api/GenericObject.java src/main/java/org/ecocean/ia/Task.java src/main/java/org/ecocean/ia/MlServiceProcessor.java src/main/java/org/ecocean/Embedding.java src/main/java/org/ecocean/ia/MatchResult.java src/main/java/org/ecocean/ia/MatchResultProspect.java src/main/webapp/WEB-INF/web.xml' in /mnt/c/Wildbook-clean2
 succeeded in 660ms:
i/lf    w/crlf  attr/                 	frontend/src/pages/MatchResultsPage/MatchResults.jsx
i/lf    w/crlf  attr/                 	frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx
i/lf    w/crlf  attr/                 	frontend/src/pages/MatchResultsPage/helperFunctions.js
i/lf    w/crlf  attr/                 	frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/Embedding.java
i/lf    w/crlf  attr/                 	src/main/java/org/ecocean/api/GenericObject.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MatchResult.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MatchResultProspect.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/MlServiceProcessor.java
i/lf    w/lf    attr/                 	src/main/java/org/ecocean/ia/Task.java
i/lf    w/lf    attr/                 	src/main/webapp/WEB-INF/web.xml

exec
/bin/bash -lc "nl -ba frontend/src/AuthenticatedSwitch.jsx | sed -n '28,90p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/MatchResults.jsx | sed -n '25,130p' && nl -ba frontend/src/pages/MatchResultsPage/MatchResults.jsx | sed -n '360,445p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js | sed -n '55,150p' && nl -ba frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js | sed -n '245,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js | sed -n '360,430p' && nl -ba frontend/src/pages/MatchResultsPage/helperFunctions.js | sed -n '1,90p'" in /mnt/c/Wildbook-clean2
 succeeded in 238ms:
    28	
    29	const BulkImport = lazy(() => import("./pages/BulkImport/BulkImport"));
    30	const BulkImportTask = lazy(() => import("./pages/BulkImport/BulkImportTask"));
    31	const MatchResults = lazy(
    32	  () => import("./pages/MatchResultsPage/MatchResults"),
    33	);
    34	
    35	const Encounter = lazy(() => import("./pages/Encounter/Encounter"));
    36	const Citation = lazy(() => import("./pages/Citation"));
    37	const PoliciesAndData = lazy(
    38	  () => import("./pages/PoliciesAndData/PoliciesAndData"),
    39	);
    40	
    41	export default function AuthenticatedSwitch({
    42	  showclassicsubmit,
    43	  showClassicEncounterSearch,
    44	  showHowToPhotograph,
    45	  siteSettingsLoading,
    46	}) {
    47	  const { data } = useGetMe();
    48	  const username = data?.username;
    49	  const avatar =
    50	    data?.imageURL || `${process.env.PUBLIC_URL}/images/Avatar.png`;
    51	  const [header, setHeader] = React.useState(true);
    52	
    53	  return (
    54	    <div className="d-flex flex-column min-vh-100">
    55	      {/* Header */}
    56	      <div
    57	        id="header"
    58	        className="position-fixed top-0 mx-auto w-100"
    59	        style={{
    60	          zIndex: "100",
    61	          height: "50px",
    62	          backgroundColor: "#303336",
    63	        }}
    64	      >
    65	        <AuthenticatedAppHeader
    66	          username={username}
    67	          avatar={avatar}
    68	          showclassicsubmit={showclassicsubmit}
    69	          showClassicEncounterSearch={showClassicEncounterSearch}
    70	          showHowToPhotograph={showHowToPhotograph}
    71	        />
    72	      </div>
    73	
    74	      {/* Main Content */}
    75	      <div
    76	        id="main-content"
    77	        className="flex-grow-1 d-flex justify-content-center"
    78	        style={{
    79	          boxSizing: "border-box",
    80	          overflow: "hidden",
    81	          paddingTop: header ? "48px" : "0",
    82	        }}
    83	      >
    84	        <Suspense fallback={<div>Loading...</div>}>
    85	          <Routes>
    86	            <Route path="/match-results" element={<MatchResults />} />
    87	            <Route path="/citation" element={<Citation />} />
    88	            <Route path="/policies-and-data" element={<PoliciesAndData />} />
    89	            <Route
    90	              path="/how-to-photograph"

 succeeded in 252ms:
    25	  const taskId = params.get("taskId");
    26	  const projectIdPrefix = params.get("projectIdPrefix");
    27	  const { data, isLoading: siteSettingsLoading } = useSiteSettings();
    28	
    29	  // Stabilize projectsForUser reference to prevent unnecessary effect re-renders
    30	  const projectsForUser = React.useMemo(
    31	    () => data?.projectsForUser ?? {},
    32	    [data?.projectsForUser],
    33	  );
    34	  const identificationRemarks = React.useMemo(
    35	    () => data?.identificationRemarks ?? [],
    36	    [data?.identificationRemarks],
    37	  );
    38	
    39	  const [filterVisible, setFilterVisible] = React.useState(false);
    40	  const [isInputFocused, setIsInputFocused] = React.useState(false);
    41	
    42	  const projectOptions = useMemo(() => {
    43	    return Object.entries(projectsForUser).map(([key, value]) => ({
    44	      value: key,
    45	      label: value?.name || key,
    46	    }));
    47	  }, [projectsForUser]);
    48	
    49	  useEffect(() => {
    50	    if (taskId) {
    51	      let initialProjectIds = [];
    52	
    53	      if (projectIdPrefix) {
    54	        if (siteSettingsLoading) return;
    55	
    56	        const match = Object.entries(projectsForUser).find(
    57	          ([, p]) => p?.prefix === projectIdPrefix,
    58	        );
    59	        if (match) {
    60	          initialProjectIds = [match[0]];
    61	        }
    62	      }
    63	
    64	      store.setTaskId(taskId);
    65	      store.setProjectNames(initialProjectIds, { fetch: false });
    66	      store.fetchMatchResults();
    67	    } else {
    68	      store.setTaskId(null);
    69	      store.setProjectNames([], { fetch: false });
    70	      store.clearResults();
    71	    }
    72	  }, [taskId, projectIdPrefix, projectsForUser, siteSettingsLoading]);
    73	
    74	  useEffect(() => {
    75	    if (!taskId || !store.shouldPoll) return;
    76	
    77	    let cancelled = false;
    78	
    79	    const scheduleNext = async () => {
    80	      if (cancelled) return;
    81	
    82	      await store.fetchMatchResults({ silent: true });
    83	
    84	      if (!cancelled && store.shouldPoll) {
    85	        setTimeout(scheduleNext, 5000);
    86	      }
    87	    };
    88	
    89	    scheduleNext();
    90	
    91	    return () => {
    92	      cancelled = true;
    93	    };
    94	  }, [taskId, store.shouldPoll]);
    95	
    96	  if (store.loading) {
    97	    return <FullScreenLoader data-testid="match-results-loader" />;
    98	  }
    99	
   100	  const showEmptyState = !store.hasDisplaySections;
   101	
   102	  return (
   103	    <Container
   104	      className="mt-2 mb-5"
   105	      id="match-results-page"
   106	      data-testid="match-results-page"
   107	    >
   108	      <InstructionsModal
   109	        show={instructionsVisible}
   110	        onHide={() => setInstructionsVisible(false)}
   111	        taskId={taskId}
   112	        themeColor={themeColor}
   113	      />
   114	
   115	      <MatchCriteriaDrawer
   116	        show={filterVisible}
   117	        onHide={() => setFilterVisible(false)}
   118	        filter={store.matchingSetFilter}
   119	      />
   120	
   121	      {store.hasResults && store.encounterId && (
   122	        <div data-testid="match-results-bottom-bar">
   123	          <MatchResultsBottomBar
   124	            store={store}
   125	            themeColor={themeColor}
   126	            identificationRemarks={identificationRemarks}
   127	          />
   128	        </div>
   129	      )}
   130	
   360	            onKeyDown={(e) => {
   361	              if (e.key === "Enter" || e.key === " ") setFilterVisible(true);
   362	            }}
   363	            aria-label="Open match criteria"
   364	          >
   365	            <FilterIcon data-testid="match-results-filter-icon" />
   366	          </div>
   367	        </div>
   368	      </div>
   369	
   370	      <div id="match-results-content" data-testid="match-results-content">
   371	        {showEmptyState ? (
   372	          <p
   373	            className="mt-3"
   374	            id="match-results-empty"
   375	            data-testid="match-results-empty"
   376	          >
   377	            <FormattedMessage
   378	              id="NO_MATCH_RESULT"
   379	              defaultMessage="No match results available."
   380	            />
   381	          </p>
   382	        ) : (
   383	          <div id="match-results-sections" data-testid="match-results-sections">
   384	            {(store.currentViewData || []).map(
   385	              ({ taskId, columns, metadata }) => (
   386	                <div
   387	                  key={`${store.viewMode}-${taskId}`}
   388	                  id={`match-results-section-${store.viewMode}-${taskId}`}
   389	                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
   390	                >
   391	                  <MatchProspectTable
   392	                    sectionId={`${store.viewMode}-${taskId}`}
   393	                    taskId={taskId}
   394	                    algorithm={metadata?.algorithm}
   395	                    numCandidates={metadata?.numCandidates}
   396	                    date={metadata?.date}
   397	                    thisEncounterImageUrl={metadata?.queryImageUrl}
   398	                    thisEncounterAnnotations={[
   399	                      metadata?.queryEncounterAnnotation,
   400	                    ]}
   401	                    thisEncounterImageAsset={metadata?.queryEncounterImageAsset}
   402	                    methodName={metadata?.methodName}
   403	                    methodDescription={metadata?.methodDescription}
   404	                    taskStatus={metadata?.taskStatus}
   405	                    taskStatusOverall={metadata?.taskStatusOverall}
   406	                    emptyStateType={metadata?.emptyStateType}
   407	                    errors={metadata?.errors}
   408	                    themeColor={themeColor}
   409	                    columns={columns}
   410	                    selectedMatch={store.selectedMatch}
   411	                    onToggleSelected={(
   412	                      checked,
   413	                      key,
   414	                      encounterId,
   415	                      individualId,
   416	                      individualDisplayName,
   417	                    ) => {
   418	                      store.setSelectedMatch(
   419	                        checked,
   420	                        key,
   421	                        encounterId,
   422	                        individualId,
   423	                        individualDisplayName,
   424	                      );
   425	                    }}
   426	                  />
   427	                </div>
   428	              ),
   429	            )}
   430	          </div>
   431	        )}
   432	      </div>
   433	    </Container>
   434	  );
   435	});
   436	
   437	export default MatchResults;

 succeeded in 250ms:
   360	    return this._selectedMatch;
   361	  }
   362	
   363	  get uniqueIndividualIds() {
   364	    const ids = new Set();
   365	
   366	    if (this._individualId) {
   367	      ids.add(this._individualId);
   368	    }
   369	
   370	    this._selectedMatch.forEach((match) => {
   371	      if (match.individualId) {
   372	        ids.add(match.individualId);
   373	      }
   374	    });
   375	
   376	    return Array.from(ids);
   377	  }
   378	
   379	  get querySelectionItem() {
   380	    if (!this._encounterId) return null;
   381	    return {
   382	      encounterId: this._encounterId,
   383	      individualId: this._individualId || null,
   384	      individualDisplayName: this.individualDisplayName || null,
   385	    };
   386	  }
   387	
   388	  get selectedIncludingQuery() {
   389	    const selected = Array.isArray(this._selectedMatch)
   390	      ? this._selectedMatch
   391	      : [];
   392	    const q = this.querySelectionItem;
   393	    if (!q) return selected;
   394	
   395	    const withoutQueryDup = selected.filter(
   396	      (m) => m?.encounterId && m.encounterId !== q.encounterId,
   397	    );
   398	
   399	    return [q, ...withoutQueryDup];
   400	  }
   401	
   402	  async fetchMatchResults({ silent = false } = {}) {
   403	    if (!this._taskId) return;
   404	
   405	    // Capture request context to detect stale responses
   406	    const requestId = Date.now() + Math.random();
   407	    this._currentRequestId = requestId;
   408	    const capturedTaskId = this._taskId;
   409	
   410	    const params = new URLSearchParams();
   411	    params.set("prospectsSize", String(this.numResults));
   412	
   413	    if (Array.isArray(this._projectNames) && this._projectNames.length > 0) {
   414	      this._projectNames.forEach((projectId) =>
   415	        params.append("projectId", projectId),
   416	      );
   417	    }
   418	
   419	    if (!silent) {
   420	      this.setLoading(true);
   421	      this.clearResults();
   422	
   423	      try {
   424	        const result = await axios.get(
   425	          `/api/v3/tasks/${this._taskId}/match-results?${params.toString()}`,
   426	        );
   427	        // Discard stale responses
   428	        if (
   429	          this._currentRequestId !== requestId ||
   430	          this._taskId !== capturedTaskId
     1	const collectProspects = (node, type, result = []) => {
     2	  if (!node) return result;
     3	
     4	  const hasMethod = !!node.method;
     5	  const taskCreated = !!node.statusOverall || hasMethod || !!node.dateCreated;
     6	  const methodName = node.method?.name ?? node.method?.description;
     7	  const methodDescription = node.method?.description ?? null;
     8	
     9	  const prospects = node.matchResults?.prospects?.[type];
    10	
    11	  const safeProspects = Array.isArray(prospects)
    12	    ? prospects.filter((p) => p && typeof p === "object")
    13	    : [];
    14	
    15	  const numberCandidatesRaw = node.matchResults?.numberCandidates;
    16	  const numberCandidates =
    17	    typeof numberCandidatesRaw === "number" ? numberCandidatesRaw : "-";
    18	
    19	  let emptyStateType = null;
    20	
    21	  if (numberCandidates === 0) {
    22	    emptyStateType = "no_candidates";
    23	  } else if (numberCandidates > 0 && safeProspects.length === 0) {
    24	    emptyStateType = "no_prospects";
    25	  }
    26	
    27	  const taskStatusOverall = node.statusOverall ?? null;
    28	  const nodeIsTerminal = isTerminalStatus(taskStatusOverall);
    29	  const nodeIsStillRunning = !!taskStatusOverall && !nodeIsTerminal;
    30	
    31	  if (taskCreated) {
    32	    const common = {
    33	      algorithm: methodName,
    34	      date: node.dateCreated,
    35	      numberCandidates: numberCandidatesRaw ?? "-",
    36	      queryEncounterId:
    37	        node.matchResults?.queryAnnotation?.encounter?.id ?? null,
    38	      encounterLocationId:
    39	        node.matchResults?.queryAnnotation?.encounter?.locationId ?? null,
    40	      matchingSetFilter: node.matchingSetFilter,
    41	      queryIndividualId:
    42	        node.matchResults?.queryAnnotation?.individual?.id ?? null,
    43	      queryIndividualDisplayName:
    44	        node.matchResults?.queryAnnotation?.individual?.displayName ?? null,
    45	      queryEncounterImageAsset:
    46	        node.matchResults?.queryAnnotation?.asset ?? null,
    47	      queryEncounterImageUrl:
    48	        node.matchResults?.queryAnnotation?.asset?.url ?? null,
    49	      queryEncounterAnnotation: node.matchResults?.queryAnnotation
    50	        ? {
    51	            id: node.matchResults.queryAnnotation?.id,
    52	            x: node.matchResults.queryAnnotation?.x,
    53	            y: node.matchResults.queryAnnotation?.y,
    54	            width: node.matchResults.queryAnnotation?.width,
    55	            height: node.matchResults.queryAnnotation?.height,
    56	            theta: node.matchResults.queryAnnotation?.theta,
    57	            boundingBox: node.matchResults.queryAnnotation?.boundingBox,
    58	            isTrivial: node.matchResults.queryAnnotation?.isTrivial,
    59	            trivial: node.matchResults.queryAnnotation?.trivial,
    60	          }
    61	        : null,
    62	      methodName,
    63	      methodDescription,
    64	      method: node.method ?? null,
    65	      taskId: node.id ?? null,
    66	      taskStatus: node.status ?? null,
    67	      taskStatusOverall,
    68	      hasResults: safeProspects.length > 0,
    69	      emptyStateType,
    70	      errors: node.statusDetails?.errors ?? null,
    71	    };
    72	
    73	    if (safeProspects.length > 0) {
    74	      result.push(
    75	        ...safeProspects.map((item) => ({
    76	          ...item,
    77	          ...common,
    78	        })),
    79	      );
    80	    } else if (nodeIsStillRunning || nodeIsTerminal) {
    81	      result.push(common);
    82	    }
    83	  }
    84	
    85	  if (Array.isArray(node.children)) {
    86	    node.children.forEach((child) => collectProspects(child, type, result));
    87	  }
    88	
    89	  return result;
    90	};

 succeeded in 258ms:
    55	
    56	  _findBestQuerySource() {
    57	    const candidates = [
    58	      ...(Array.isArray(this._indivResults) ? this._indivResults : []),
    59	      ...(Array.isArray(this._annotResults) ? this._annotResults : []),
    60	    ];
    61	
    62	    if (candidates.length === 0) return null;
    63	
    64	    const withEncounterId = candidates.find((item) => item?.queryEncounterId);
    65	    if (withEncounterId) return withEncounterId;
    66	
    67	    const withQueryAnnotationEncounter = candidates.find(
    68	      (item) => item?.queryEncounterAnnotation?.encounter?.id,
    69	    );
    70	    if (withQueryAnnotationEncounter) return withQueryAnnotationEncounter;
    71	
    72	    const withSomeQueryContext = candidates.find(
    73	      (item) =>
    74	        item?.queryEncounterImageAsset ||
    75	        item?.queryEncounterImageUrl ||
    76	        item?.queryEncounterAnnotation ||
    77	        item?.matchingSetFilter ||
    78	        item?.queryIndividualId,
    79	    );
    80	    if (withSomeQueryContext) return withSomeQueryContext;
    81	
    82	    return candidates[0];
    83	  }
    84	
    85	  loadData(results, { preserveSelection = false } = {}) {
    86	    const root = results?.matchResultsRoot;
    87	
    88	    this._annotResults = getAllAnnot(root);
    89	    this._indivResults = getAllIndiv(root);
    90	    this._taskStillRunning = isMatchTaskStillRunning(root);
    91	    this._taskHasError = hasMatchTaskError(root);
    92	    this._taskIsTerminal = isMatchTaskTerminal(root);
    93	
    94	    const rootHasChildren =
    95	      Array.isArray(root?.children) && root.children.length > 0;
    96	    const rootHasMatchResults = !!root?.matchResults;
    97	
    98	    this._rootStillRunning =
    99	      !rootHasChildren &&
   100	      !rootHasMatchResults &&
   101	      !!root?.statusOverall &&
   102	      root.statusOverall !== "completed" &&
   103	      root.statusOverall !== "error";
   104	
   105	    this._rootHasError =
   106	      !rootHasChildren &&
   107	      !rootHasMatchResults &&
   108	      root?.statusOverall === "error";
   109	
   110	    const hasAnyResults =
   111	      (Array.isArray(this._annotResults) && this._annotResults.length > 0) ||
   112	      (Array.isArray(this._indivResults) && this._indivResults.length > 0);
   113	
   114	    if (!hasAnyResults) {
   115	      this._rawAnnots = [];
   116	      this._rawIndivs = [];
   117	      this._processedAnnots = [];
   118	      this._processedIndivs = [];
   119	      this._encounterId = null;
   120	      this._matchingSetFilter = {};
   121	      this._individualId = null;
   122	      this._individualDisplayName = null;
   123	      this._hasResults = false;
   124	      this._encounterLocationId = "";
   125	      this._statusOverall = root?.statusOverall || "";
   126	
   127	      if (!preserveSelection) {
   128	        this.resetSelectionToQuery();
   129	      }
   130	      return;
   131	    }
   132	
   133	    if (!this._annotResults || this._annotResults.length === 0) {
   134	      this._viewMode = "individual";
   135	    }
   136	    if (!this._indivResults || this._indivResults.length === 0) {
   137	      this._viewMode = "image";
   138	    }
   139	
   140	    const querySource = this._findBestQuerySource();
   141	    if (!querySource) return;
   142	
   143	    this._encounterId =
   144	      querySource.queryEncounterId ||
   145	      querySource.queryEncounterAnnotation?.encounter?.id ||
   146	      null;
   147	
   148	    this._encounterLocationId = querySource.encounterLocationId || "";
   149	    this._matchingSetFilter = querySource.matchingSetFilter || {};
   150	    this._individualId = querySource.queryIndividualId || null;
   245	    this._individualDisplayName = null;
   246	    this._statusOverall = "";
   247	    this._viewMode = "individual";
   248	    this._newIndividualName = "";
   249	    this._hasResults = false;
   250	    this._taskStillRunning = false;
   251	    this._taskHasError = false;
   252	    this._taskIsTerminal = false;
   253	    this._rootStillRunning = false;
   254	    this._rootHasError = false;
   255	
   256	    this.resetSelectionToQuery();
   257	  }
   258	
   259	  // --- computed data for UI ---
   260	
   261	  get processedAnnots() {
   262	    return this._processedAnnots;
   263	  }
   264	
   265	  get processedIndivs() {
   266	    return this._processedIndivs;
   267	  }
   268	
   269	  get currentViewData() {
   270	    return this._viewMode === "individual"
   271	      ? this._processedIndivs
   272	      : this._processedAnnots;
   273	  }
   274	
   275	  get viewMode() {
   276	    return this._viewMode;
   277	  }
   278	
   279	  get taskStillRunning() {
   280	    return this._taskStillRunning;
   281	  }
   282	
   283	  get taskHasError() {
   284	    return this._taskHasError;
   285	  }
   286	
   287	  get taskIsTerminal() {
   288	    return this._taskIsTerminal;
   289	  }
   290	
   291	  get rootStillRunning() {
   292	    return this._rootStillRunning;
   293	  }
   294	
   295	  get rootHasError() {
   296	    return this._rootHasError;
   297	  }
   298	
   299	  get shouldPoll() {
   300	    return !!this._taskId && this._taskStillRunning;
   301	  }
   302	
   303	  get hasDisplaySections() {
   304	    return this.currentViewData.length > 0;
   305	  }
   306	
   307	  get encounterId() {
   308	    return this._encounterId;
   309	  }
   310	
   311	  get encounterLocationId() {
   312	    return this._encounterLocationId;
   313	  }
   314	
   315	  get matchingSetFilter() {
   316	    return this._matchingSetFilter;
   317	  }
   318	
   319	  get individualId() {
   320	    return this._individualId;
   321	  }
   322	
   323	  get individualDisplayName() {
   324	    return this._individualDisplayName;
   325	  }
   326	
   327	  get projectNames() {
   328	    return this._projectNames;
   329	  }
   330	

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/Task.java | sed -n '740,885p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx | sed -n '200,330p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/api/GenericObject.java | sed -n '25,135p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '555,625p' && nl -ba src/main/java/org/ecocean/ia/MatchResultProspect.java | sed -n '80,96p'" in /mnt/c/Wildbook-clean2
 succeeded in 241ms:
    25	// note: this is for use on any non-Base object
    26	// see api/BaseObject if object extends Base.java
    27	public class GenericObject extends ApiBase {
    28	    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    29	    throws ServletException, IOException {
    30	        String context = ServletUtilities.getContext(request);
    31	        // normally false for GET, but some deep behavior creates objects on-the-fly
    32	        // and therefore needs to commit to db
    33	        boolean commitShepherd = false;
    34	        Shepherd myShepherd = new Shepherd(context);
    35	
    36	        myShepherd.setAction("api.GenericObject.doGet");
    37	        myShepherd.beginDBTransaction();
    38	
    39	        String uri = request.getRequestURI();
    40	        String[] args = uri.substring(8).split("/");
    41	        if (args.length < 1) throw new ServletException("bad path");
    42	        JSONObject rtn = new JSONObject();
    43	        rtn.put("success", false);
    44	        try {
    45	            User currentUser = myShepherd.getUser(request);
    46	            switch (args[0]) {
    47	            case "media-assets":
    48	                if (currentUser == null) {
    49	                    rtn.put("statusCode", 401);
    50	                    rtn.put("error", "access denied");
    51	                } else {
    52	                    MediaAsset ma = null;
    53	                    URL url = null;
    54	                    try {
    55	                        ma = MediaAssetFactory.load(Integer.parseInt(args[1]), myShepherd);
    56	                        if (ma != null) url = ma.safeURL(myShepherd, request);
    57	                    } catch (Exception ex) {
    58	                        throw new ApiException(ex.toString());
    59	                    }
    60	                    if (ma == null) {
    61	                        rtn.put("statusCode", 404);
    62	                        rtn.put("error", "not found");
    63	                    } else {
    64	                        rtn.put("success", true);
    65	                        rtn.put("statusCode", 200);
    66	                        rtn.put("url", url.toString());
    67	                        rtn.put("width", ma.getWidth());
    68	                        rtn.put("height", ma.getHeight());
    69	                        rtn.put("rotationInfo", ma.getRotationInfo());
    70	                        JSONArray janns = new JSONArray();
    71	                        for (Annotation ann : ma.getAnnotations()) {
    72	                            JSONObject jann = new JSONObject();
    73	                            if (ann.getFeatures() != null) {
    74	                                for (Feature ft : ann.getFeatures()) {
    75	                                    if (ft.isUnity()) {
    76	                                        jann.put("trivial", true);
    77	                                        jann.put("x", 0);
    78	                                        jann.put("y", 0);
    79	                                        jann.put("width", (int)ma.getWidth());
    80	                                        jann.put("height", (int)ma.getHeight());
    81	                                    } else {
    82	                                        // basically if we have more than one feature, only one wins
    83	                                        if (ft.getParameters() != null) jann = ft.getParameters();
    84	                                    }
    85	                                }
    86	                            }
    87	                            Encounter enc = ann.findEncounter(myShepherd);
    88	                            if (enc != null) {
    89	                                jann.put("encounterId", enc.getId());
    90	                                jann.put("encounterTaxonomy", enc.getTaxonomyString());
    91	                            }
    92	                            jann.put("id", ann.getId());
    93	                            janns.put(jann);
    94	                        }
    95	                        rtn.put("annotations", janns);
    96	                    }
    97	                }
    98	                break;
    99	            case "tasks":
   100	                if (currentUser == null) {
   101	                    rtn.put("statusCode", 401);
   102	                    rtn.put("error", "access denied");
   103	                } else {
   104	                    if ((args.length > 2) && ("match-results".equals(args[2]))) {
   105	                        Task task = myShepherd.getTask(args[1]);
   106	                        if (task == null) {
   107	                            rtn.put("statusCode", 404);
   108	                            rtn.put("error", "not found");
   109	                        } else {
   110	                            // right now we replicate legacy functionality and allow access to anyone
   111	                            // based on task id only, rather than blocking based on task.canUserAccess()
   112	                            int prospectsSize = org.ecocean.ia.MatchResult.DEFAULT_PROSPECTS_CUTOFF;
   113	                            Set<String> projectIds = null;
   114	                            String[] pvals = request.getParameterValues("projectId");
   115	                            if ((pvals != null) && (pvals.length > 0))
   116	                                projectIds = new HashSet<String>(Arrays.asList(
   117	                                    request.getParameterValues("projectId")));
   118	                            try {
   119	                                // note: negative size means all of them (no cutoff)
   120	                                prospectsSize = Integer.parseInt(request.getParameter(
   121	                                    "prospectsSize"));
   122	                            } catch (NumberFormatException ex) {}
   123	                            rtn.put("prospectsSize", prospectsSize);
   124	                            JSONObject mrJson = task.matchResultsJson(prospectsSize, projectIds,
   125	                                myShepherd);
   126	                            rtn.put("projectIds", projectIds);
   127	                            rtn.put("matchResultsRoot", mrJson);
   128	                            rtn.put("success", true);
   129	                            rtn.put("statusCode", 200);
   130	                            // this means we created on-the-fly some MatchResult(s) that need persisting
   131	                            commitShepherd = (mrJson != null) &&
   132	                                mrJson.optBoolean("_commitShepherd", false);
   133	                            if (commitShepherd) myShepherd.commitDBTransaction();
   134	                        }
   135	                    } else {

 succeeded in 255ms:
   740	        if (getParameters() == null) return null;
   741	        return getParameters().optJSONObject("matchingSetFilter");
   742	    }
   743	
   744	    public JSONObject getIdentificationMethodInfo() {
   745	        if (getParameters() == null) return null;
   746	        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
   747	        JSONObject rtn = new JSONObject();
   748	        // vector/embed flavor
   749	        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
   750	            null) != null) {
   751	            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
   752	                "model_id", null);
   753	            if (modelId == null) {
   754	                rtn.put("description", "Vector embedding match");
   755	            } else {
   756	                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
   757	                rtn.put("modelId", modelId);
   758	            }
   759	            return rtn;
   760	        }
   761	        // it seems both of these are in most logs (and are identical), but being safe in case there are
   762	        // examples in the wild with only one
   763	        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   764	            "query_config_dict");
   765	        if (conf == null)
   766	            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
   767	                "queryConfigDict");
   768	        // we set HotSpotter if pipeline_root is not set here
   769	        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
   770	        rtn.put("description",
   771	            getParameters().getJSONObject("ibeis.identification").optString("description",
   772	            "unknown algorithm/method"));
   773	        return rtn;
   774	    }
   775	
   776	    // convenience
   777	    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
   778	        return myShepherd.getMatchResults(this);
   779	    }
   780	
   781	    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
   782	        List<MatchResult> all = myShepherd.getMatchResults(this);
   783	
   784	        if (Util.collectionIsEmptyOrNull(all)) return null;
   785	        return all.get(0);
   786	    }
   787	
   788	    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
   789	    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
   790	        List<MatchResult> mrs = new ArrayList<MatchResult>();
   791	        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
   792	            myShepherd);
   793	
   794	        if (logs == null) return mrs;
   795	        for (IdentityServiceLog log : logs) {
   796	            JSONObject res = log.getJsonResult();
   797	            // in theory this is how we can tell if it is an ident result log versus detection
   798	            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
   799	                try {
   800	                    MatchResult mr = new MatchResult(log, myShepherd);
   801	                    System.out.println("[INFO] generateMatchResults() [log t=" +
   802	                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
   803	                    myShepherd.getPM().makePersistent(mr);
   804	                    mrs.add(mr);
   805	                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
   806	                        log.getTimestamp());
   807	                } catch (java.io.IOException ex) {
   808	                    System.out.println("[ERROR] generateMatchResults() [log t=" +
   809	                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
   810	                    ex.printStackTrace();
   811	                    setStatusDetailsAddError("UNKNOWN",
   812	                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
   813	                        " failed due to: " + ex);
   814	                }
   815	            }
   816	        }
   817	        return mrs;
   818	    }
   819	
   820	    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   821	        JSONObject rtn = new JSONObject();
   822	
   823	        rtn.put("id", getId());
   824	        rtn.put("parentTaskId", getParentId());
   825	        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
   826	        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
   827	        rtn.put("timeInactiveMillis", timeInactive());
   828	        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
   829	        // we should have it here, hence this debugging value ... possible optimization for later
   830	        if (hasObjectAnnotations()) {
   831	            JSONArray annotArr = new JSONArray();
   832	            for (Annotation ann : getObjectAnnotations()) {
   833	                if (ann != null) annotArr.put(ann.getId());
   834	            }
   835	            rtn.put("__taskAnnotations", annotArr);
   836	        }
   837	        JSONObject methodInfo = getIdentificationMethodInfo();
   838	        // we basically use this to determine if we are "identification-like" enough
   839	        // to display extended details
   840	        if (methodInfo != null) {
   841	            rtn.put("method", methodInfo);
   842	            rtn.put("matchingSetFilter", getMatchingSetFilter());
   843	/*
   844	            1. we only care about (and importantly try to generate) MatchResults for ident type *with no children*
   845	               (as there may be non-leaf nodes with methodInfo)
   846	 * note: we try getting it regardless of children ("just in case"); but only try to generate if none
   847	            2. getLatestMatchResult() and generateMatchResults() only pertain to log-based (wbia) results,
   848	               as vector results should have generated their MatchResult upon completion
   849	 */
   850	            MatchResult mr = getLatestMatchResult(myShepherd);
   851	            if ((mr == null) && !hasChildren()) {
   852	                System.out.println(
   853	                    "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
   854	                    this.getId());
   855	                List<MatchResult> mrs = generateMatchResults(myShepherd);
   856	                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
   857	                if (mrs.size() > 0) {
   858	                    mr = mrs.get(mrs.size() - 1);
   859	                    // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
   860	                    rtn.put("_commitShepherd", true);
   861	                }
   862	            }
   863	            if (mr != null)
   864	                rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
   865	        }
   866	        // now we recurse thru children if applicable
   867	        if (hasChildren()) {
   868	            JSONArray charr = new JSONArray();
   869	            for (Task child : children) {
   870	                // TODO decide if we need to process child????
   871	                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
   872	                // we have to bubble this up all the way to the toplevel  :/
   873	                if (childJson.optBoolean("_commitShepherd", false))
   874	                    rtn.put("_commitShepherd", true);
   875	                charr.put(childJson);
   876	            }
   877	            rtn.put("children", charr);
   878	            // if we dont have children (leaf nodes) we get the status
   879	        } else {
   880	            // unsure which of these two things is more accurate or useful; thus including both
   881	            rtn.put("status", getStatus(myShepherd));
   882	            rtn.put("statusOverall", getOverallStatus(myShepherd));
   883	            rtn.put("statusDetails", getStatusDetails());
   884	        }
   885	        return rtn;

 succeeded in 253ms:
   200	
   201	  const hasProspects = columns.some((columnData) =>
   202	    columnData.some((candidate) => candidate?.annotation),
   203	  );
   204	
   205	  const [previewedRow, setPreviewedRow] = useState(() => {
   206	    const first =
   207	      columns
   208	        .flatMap((columnData) => columnData)
   209	        .find((candidate) => candidate?.annotation) ?? null;
   210	    if (!first) return null;
   211	    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
   212	    return { ...first, _rowKey: firstKey };
   213	  });
   214	
   215	  const [inspectorOpen, setInspectorOpen] = useState(false);
   216	  const inspectorUrl = previewedRow?.asset?.url;
   217	  const inspectorOrigW = previewedRow?.asset?.width;
   218	  const inspectorOrigH = previewedRow?.asset?.height;
   219	
   220	  React.useEffect(() => {
   221	    const flat = columns.flatMap((columnData) => columnData);
   222	    const candidates = flat.filter((candidate) => candidate?.annotation);
   223	
   224	    if (candidates.length === 0) {
   225	      setPreviewedRow(null);
   226	      return;
   227	    }
   228	
   229	    setPreviewedRow((prev) => {
   230	      if (prev?.annotation?.id) {
   231	        const matched = candidates.find(
   232	          (candidate) => candidate?.annotation?.id === prev.annotation.id,
   233	        );
   234	
   235	        if (matched) {
   236	          const matchedKey = `${matched.annotation?.id}-${matched.displayIndex}`;
   237	          return { ...matched, _rowKey: matchedKey };
   238	        }
   239	      }
   240	
   241	      const first = candidates[0];
   242	      const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
   243	      return { ...first, _rowKey: firstKey };
   244	    });
   245	  }, [columns]);
   246	
   247	  const [hoveredRow, setHoveredRow] = React.useState(null);
   248	
   249	  const handleRowClick = (rowData, rowKey) => {
   250	    setPreviewedRow({ ...rowData, _rowKey: rowKey });
   251	    rightOverlayRef.current?.reset?.();
   252	  };
   253	
   254	  const isSelected = (rowKey) => selectedMatch?.some((d) => d.key === rowKey);
   255	
   256	  const rightAnnotations = React.useMemo(() => {
   257	    const ann = previewedRow?.annotation;
   258	    if (!ann) return [];
   259	    return [
   260	      {
   261	        id: ann.id,
   262	        boundingBox: ann.boundingBox,
   263	        x: ann.x,
   264	        y: ann.y,
   265	        width: ann.width,
   266	        height: ann.height,
   267	        theta: ann.theta,
   268	        trivial: ann.isTrivial || ann.trivial,
   269	      },
   270	    ];
   271	  }, [previewedRow]);
   272	
   273	  const rightImageUrl = previewedRow?.annotation?.asset?.url;
   274	
   275	  const leftOrigW =
   276	    thisEncounterImageAsset?.attributes?.width ??
   277	    thisEncounterImageAsset?.width;
   278	  const leftOrigH =
   279	    thisEncounterImageAsset?.attributes?.height ??
   280	    thisEncounterImageAsset?.height;
   281	
   282	  const leftAnnotations = thisEncounterAnnotations;
   283	  const leftRotationInfo = thisEncounterImageAsset?.rotationInfo;
   284	
   285	  const rightOrigW =
   286	    previewedRow?.annotation?.asset?.width ??
   287	    previewedRow?.annotation?.asset?.attributes?.width;
   288	  const rightOrigH =
   289	    previewedRow?.annotation?.asset?.height ??
   290	    previewedRow?.annotation?.asset?.attributes?.height;
   291	
   292	  const leftImageUrl = thisEncounterImageUrl;
   293	  const hasLeftImage = Boolean(leftImageUrl);
   294	  const hasRightImage = Boolean(rightImageUrl);
   295	
   296	  const openFullscreen = () => {
   297	    if (!hasRightImage) return;
   298	    setFullscreenOpen(true);
   299	  };
   300	
   301	  React.useEffect(() => {
   302	    if (!fullscreenOpen) return;
   303	    fsLeftRef.current?.reset?.();
   304	    fsRightRef.current?.reset?.();
   305	  }, [fullscreenOpen]);
   306	
   307	  const isStillRunning =
   308	    !!taskStatusOverall &&
   309	    taskStatusOverall !== "completed" &&
   310	    taskStatusOverall !== "error";
   311	
   312	  const isError = taskStatusOverall === "error";
   313	
   314	  return (
   315	    <div
   316	      className="mb-4"
   317	      id={sectionId}
   318	      data-testid={`match-prospect-table-${sectionId}`}
   319	    >
   320	      <div
   321	        className="d-flex justify-content-between align-items-center mb-2"
   322	        data-testid={`match-prospect-header-${sectionId}`}
   323	      >
   324	        <div className="d-flex w-100">
   325	          <div
   326	            style={{ fontWeight: "500" }}
   327	            data-testid={`match-prospect-method-${sectionId}`}
   328	          >
   329	            {methodDescription
   330	              ? `${matchesBasedOnText} ${methodDescription}`

 succeeded in 297ms:
   555	        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
   556	        return pros;
   557	    }
   558	
   559	    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   560	        JSONObject sj = new JSONObject();
   561	
   562	        for (String type : prospectScoreTypes()) {
   563	            JSONArray jarr = new JSONArray();
   564	            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
   565	                jarr.put(mrp.jsonForApiGet(myShepherd));
   566	            }
   567	            sj.put(type, jarr);
   568	        }
   569	        return sj;
   570	    }
   571	
   572	    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
   573	        JSONObject rtn = new JSONObject();
   574	
   575	        rtn.put("id", id);
   576	        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
   577	        rtn.put("numberTotalProspects", numberProspects());
   578	        rtn.put("numberCandidates", getNumberCandidates());
   579	        rtn.put("created", Util.millisToISO8601String(created));
   580	        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
   581	        rtn.put("projectIds", projectIds);
   582	        return rtn;
   583	    }
   584	
   585	    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
   586	        JSONObject aj = new JSONObject();
   587	
   588	        if (ann == null) return aj;
   589	        MediaAsset ma = ann.getMediaAsset();
   590	        // populate bounding box stuff (note: it may reset aj so must be done first)
   591	        if (ann.getFeatures() != null) {
   592	            for (Feature ft : ann.getFeatures()) {
   593	                if (ft.isUnity()) {
   594	                    aj.put("trivial", true);
   595	                    aj.put("x", 0);
   596	                    aj.put("y", 0);
   597	                    // would be weird to be null, but.....
   598	                    if (ma != null) {
   599	                        aj.put("width", (int)ma.getWidth());
   600	                        aj.put("height", (int)ma.getHeight());
   601	                    }
   602	                } else {
   603	                    // basically if we have more than one feature, only one wins
   604	                    if (ft.getParameters() != null) aj = ft.getParameters();
   605	                }
   606	            }
   607	        }
   608	        if (ma != null) {
   609	            JSONObject mj = ma.toSimpleJSONObject();
   610	            mj.put("rotationInfo", ma.getRotationInfo());
   611	            aj.put("asset", mj);
   612	        }
   613	        Encounter enc = ann.findEncounter(myShepherd);
   614	        if (enc != null) {
   615	            JSONObject ej = new JSONObject();
   616	            // TODO add "access" permission value if needed?
   617	            ej.put("id", enc.getId());
   618	            ej.put("taxonomy", enc.getTaxonomyString());
   619	            ej.put("locationId", enc.getLocationID());
   620	            aj.put("encounter", ej);
   621	            MarkedIndividual indiv = enc.getIndividual();
   622	            if (indiv != null) {
   623	                JSONObject ij = new JSONObject();
   624	                ij.put("id", indiv.getId());
   625	                ij.put("taxonomy", indiv.getTaxonomyString());
    80	    }
    81	
    82	    public JSONObject jsonForApiGet(Shepherd myShepherd) {
    83	        JSONObject rtn = new JSONObject();
    84	
    85	        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd));
    86	        rtn.put("score", score);
    87	        // skipping scoreType since this is currently only used filtered by scoreType already
    88	        if (asset != null) {
    89	            JSONObject aj = asset.toSimpleJSONObject();
    90	            aj.put("url", asset.webURL()); // we have no "safe" url
    91	            rtn.put("asset", aj);
    92	        }
    93	        return rtn;
    94	    }
    95	
    96	    // used in sorting

exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MlServiceProcessor.java | sed -n '495,535p' && nl -ba src/main/java/org/ecocean/Embedding.java | sed -n '284,300p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/IA.java | sed -n '232,312p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/main/java/org/ecocean/ia/MatchResult.java | sed -n '204,260p' && nl -ba src/test/java/org/ecocean/MatchResultTest.java | sed -n '80,98p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba src/test/java/org/ecocean/api/TaskMatchResults.java | sed -n '136,170p' && nl -ba frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx | sed -n '30,55p' && nl -ba frontend/src/__tests__/pages/MatchResults/MatchResults.test.jsx | sed -n '160,184p'" in /mnt/c/Wildbook-clean2
 succeeded in 277ms:
   232	     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
   233	     * topTask. The topTask itself remains as the aggregator that holds the
   234	     * full MediaAsset list for caller-side summary code.</p>
   235	     *
   236	     * <p>encounterId is derived best-effort from the MediaAsset's existing
   237	     * trivial annotation (every Encounter.addMediaAsset call creates one).
   238	     * If null, MlServiceProcessor persists annotations without explicit
   239	     * Encounter linkage and downstream MediaAsset.assignEncounters handles
   240	     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
   241	     */
   242	    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
   243	        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
   244	        int queued = 0;
   245	        for (MediaAsset ma : mas) {
   246	            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
   247	                queued++;
   248	            }
   249	        }
   250	        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
   251	            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
   252	        return topTask;
   253	    }
   254	
   255	    /**
   256	     * Build and enqueue one v2 ml-service job for a single MediaAsset.
   257	     * Returns {@code true} iff the FileQueue write succeeded.
   258	     *
   259	     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
   260	     * normal intake path) and the startup stale-mlservice reconciler in
   261	     * {@code StartupWildbook}. The reconciler relies on the boolean
   262	     * return to decide whether to commit accompanying state changes; the
   263	     * normal intake path tolerates the swallowed-failure behavior.</p>
   264	     *
   265	     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
   266	     * internally commits/reopens the transaction, so the child Task row
   267	     * is persisted before this method enqueues. On enqueue failure the
   268	     * child Task remains in the DB as an orphan — there is no queued
   269	     * job that will ever drive it. The orphan IS still discoverable
   270	     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
   271	     * the task references the MediaAsset through objectMediaAssets),
   272	     * so it may surface in operator-facing task listings until cleaned
   273	     * up by an out-of-band path. Callers that need cleanup should
   274	     * delete the orphan explicitly; the default posture here is to
   275	     * accept it since FileQueue write failures are rare.</p>
   276	     *
   277	     * <p>If {@code topTask} is null a fresh root task is created inside
   278	     * this method. This matches the reconciler's use case where there is
   279	     * no caller-side aggregator umbrella.</p>
   280	     */
   281	    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
   282	        Taxonomy taxy, Task topTask, String context, String baseUrl) {
   283	        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
   284	        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
   285	        singleton.add(ma);
   286	        childTask.setObjectMediaAssets(singleton);
   287	        myShepherd.storeNewTask(childTask);
   288	
   289	        // Best-effort encounterId via existing annotations on the MA.
   290	        String encounterId = null;
   291	        ArrayList<Annotation> existing = ma.getAnnotations();
   292	        if (existing != null) {
   293	            for (Annotation a : existing) {
   294	                Encounter enc = a.findEncounter(myShepherd);
   295	                if (enc != null) {
   296	                    encounterId = enc.getId();
   297	                    break;
   298	                }
   299	            }
   300	        }
   301	
   302	        JSONObject qjob = new JSONObject();
   303	        qjob.put("mlServiceV2", true);
   304	        qjob.put("mediaAssetId", ma.getId());
   305	        qjob.put("taxonomyString", taxy.getScientificName());
   306	        qjob.put("taskId", childTask.getId());
   307	        qjob.put("__context", context);
   308	        qjob.put("__baseUrl", baseUrl);
   309	        if (Util.stringExists(encounterId)) {
   310	            qjob.put("encounterId", encounterId);
   311	        }
   312	

 succeeded in 290ms:
   495	    public MlServiceJobOutcome runMatchProspects(List<String> annotationIds, String taskId,
   496	        JSONObject matchConfig) {
   497	        if (annotationIds == null || annotationIds.isEmpty()) {
   498	            markTaskCompleted(taskId);
   499	            return MlServiceJobOutcome.ok(new ArrayList<String>());
   500	        }
   501	
   502	        Shepherd shep = new Shepherd(context);
   503	        shep.setAction(ACTION_PREFIX + "runMatchProspects");
   504	        try {
   505	            shep.beginDBTransaction();
   506	            List<Annotation> anns = new ArrayList<Annotation>();
   507	            for (String annId : annotationIds) {
   508	                Annotation ann = shep.getAnnotation(annId);
   509	                if (ann != null) anns.add(ann);
   510	            }
   511	            if (anns.isEmpty()) {
   512	                Task task = Task.load(taskId, shep);
   513	                markTaskDroppedStale(shep, task, "annotations missing");
   514	                shep.commitDBTransaction();
   515	                return MlServiceJobOutcome.stale("annotations missing");
   516	            }
   517	
   518	            Task parent = Task.load(taskId, shep);
   519	            Task matchTask = (parent == null) ? new Task() : new Task(parent);
   520	            matchTask.setObjectAnnotations(anns);
   521	            matchTask.addParameter("mlServiceV2Match", true);
   522	            shep.getPM().makePersistent(matchTask);
   523	            // findMatchProspects returns false when the match config is not
   524	            // a vector config or matchConfig is null. Don't leave the match
   525	            // task without a terminal status — mark the parent task error.
   526	            boolean ran = Embedding.findMatchProspects(matchConfig, matchTask, shep);
   527	            if (!ran) {
   528	                matchTask.setStatus("error");
   529	                matchTask.setStatusDetailsAddError("INVALID_MATCH_CONFIG",
   530	                    "findMatchProspects rejected match config: " +
   531	                    (matchConfig == null ? "null" : matchConfig.toString()));
   532	                matchTask.setCompletionDateInMilliseconds();
   533	                // Update the parent task in this same transaction (parent is
   534	                // already loaded above) so the two updates commit atomically.
   535	                // Splitting across transactions risks leaving the parent
   284	        // reflects reality (plan v2 §commit #3: previous code unconditionally
   285	        // marked the parent "completed" even if every subtask failed).
   286	        int subtasksOk = 0;
   287	        int subtasksFailed = 0;
   288	        for (Annotation ann : task.getObjectAnnotations()) {
   289	            // every ann gets a subTask
   290	            Task subTask = new Task(task);
   291	            subTask.addObject(ann);
   292	            // we need embedding(s) on this annot to find prospects, so lets try to make some on the fly if we dont have one
   293	            // TODO not sure if this is wise, or it would be better to just fail outright and let some background process do this
   294	            if (ann.numberEmbeddings() < 1) {
   295	                System.out.println(
   296	                    "[DEBUG] findMatchProspects() creating embeddings on the fly; found none on " +
   297	                    ann);
   298	                try {
   299	                    ann.extractEmbeddings(myShepherd);
   300	                } catch (IAException ex) {

 succeeded in 289ms:
   204	        Shepherd myShepherd)
   205	    throws IOException {
   206	        if (Util.collectionIsEmptyOrNull(annots)) return 0;
   207	        if (this.prospects == null)
   208	            this.prospects = new HashSet<MatchResultProspect>();
   209	        if (scoreByIndividual) {
   210	            // the scores for these are calculated weighted by indiv count
   211	            _populateProspectsByIndividual(annots, myShepherd);
   212	        } else {
   213	            // these scores are direct from opensearch
   214	            for (Annotation ann : annots) {
   215	                this.prospects.add(new MatchResultProspect(ann, ann.getOpensearchScore(), "annot",
   216	                    null));
   217	            }
   218	        }
   219	        this.numberProspects = this.prospects.size();
   220	        return this.numberProspects;
   221	    }
   222	
   223	    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
   224	        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
   225	            List<Annotation> >();
   226	
   227	        for (Annotation ann : annots) {
   228	            Encounter enc = ann.findEncounter(myShepherd);
   229	            // i think we just ignore if no enc/indiv
   230	            if (enc == null) continue;
   231	            MarkedIndividual indiv = enc.getIndividual();
   232	            if (indiv == null) continue;
   233	            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
   234	            tally.get(indiv).add(ann);
   235	        }
   236	        if (tally.size() < 1) return; // no individuals i guess?
   237	
   238	        // this sorts by most annots (per indiv) highest to lowest
   239	        List<Map.Entry<MarkedIndividual,
   240	            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
   241	        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
   242	        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
   243	            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
   244	            Map.Entry<MarkedIndividual, List<Annotation> > two) {
   245	                // we reverse order here so we get largest first
   246	                return Integer.compare(two.getValue().size(), one.getValue().size());
   247	            }
   248	        });
   249	        int most = sorted.get(0).getValue().size(); // top num of annots
   250	        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
   251	            double score = new Double(ent.getValue().size()) / new Double(most);
   252	            // the ent value (annot List) should always have at least one annot, so we use first one.
   253	            // Inspection MediaAsset attached later by MatchInspectionPairxEnricher
   254	            // (empty-match-prospects design Track 2 C13).
   255	            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
   256	                null));
   257	        }
   258	    }
   259	
   260	    /**
    80	    throws IOException {
    81	        Task task = mock(Task.class);
    82	
    83	        when(task.countObjectAnnotations()).thenReturn(1);
    84	        int numCand = 99;
    85	        Annotation ann = mock(Annotation.class);
    86	        ArrayList<Annotation> annList = new ArrayList<Annotation>();
    87	
    88	        annList.add(ann);
    89	        when(task.getObjectAnnotations()).thenReturn(annList);
    90	
    91	        MatchResult mr = new MatchResult(task, annList, numCand, null);
    92	        assertTrue(mr.getNumberCandidates() == numCand);
    93	        assertTrue(mr.numberProspects() == 1);
    94	        // FIXME someday we need to figure out indiv-vector-search
    95	        // assertTrue(mr.prospectScoreTypes().contains("indiv"));
    96	        assertTrue(mr.prospectScoreTypes().contains("annot"));
    97	    }
    98	

 succeeded in 291ms:
   136	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
   137	                apiServlet.doGet(mockRequest, mockResponse);
   138	                responseOut.flush();
   139	                JSONObject jout = new JSONObject(responseOut.toString());
   140	                verify(mockResponse).setStatus(404);
   141	                assertFalse(jout.getBoolean("success"));
   142	            }
   143	        }
   144	    }
   145	
   146	    @Test void apiGetSuccess()
   147	    throws ServletException, IOException {
   148	        User user = mock(User.class);
   149	
   150	        // this doesnt really test the output value of "matchResultsRoot", since we
   151	        // test task.matchResultsJson() seperately so dont care about the null value here
   152	        Task task = mock(Task.class);
   153	        String id = "ok-id";
   154	
   155	        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
   156	        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
   157	
   158	        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
   159	                (mock, context) -> {
   160	            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
   161	            when(mock.getTask(any(String.class))).thenReturn(task);
   162	        })) {
   163	            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
   164	                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
   165	                apiServlet.doGet(mockRequest, mockResponse);
   166	                responseOut.flush();
   167	                JSONObject jout = new JSONObject(responseOut.toString());
   168	                verify(mockResponse).setStatus(200);
   169	                assertTrue(jout.getBoolean("success"));
   170	            }
    30	    return React.createElement("div", { "data-testid": "full-screen-loader" });
    31	  }
    32	  FullScreenLoader.displayName = "FullScreenLoader";
    33	  return FullScreenLoader;
    34	});
    35	
    36	jest.mock(
    37	  "../../../pages/MatchResultsPage/components/MatchProspectTable",
    38	  () => {
    39	    const React = require("react");
    40	    function MatchProspectTable({ taskId, columns, onToggleSelected }) {
    41	      return React.createElement(
    42	        "div",
    43	        { "data-testid": "prospect-table-" + taskId },
    44	        (columns || []).flat().map(function (col, i) {
    45	          return React.createElement(
    46	            "button",
    47	            {
    48	              key: i,
    49	              "data-testid": "prospect-row",
    50	              onClick: function () {
    51	                if (onToggleSelected) {
    52	                  onToggleSelected(
    53	                    true,
    54	                    "key-" + i,
    55	                    "enc-" + i,
   160	  matchResultsRoot: {
   161	    id: "task-1",
   162	    status: "complete",
   163	    statusOverall: "complete",
   164	    dateCreated: "2024-06-01",
   165	    method: { name: "hotspotter", description: "HotSpotter" },
   166	    matchingSetFilter: {},
   167	    matchResults: {
   168	      numberCandidates: 10,
   169	      queryAnnotation: {
   170	        x: 0.1,
   171	        y: 0.2,
   172	        width: 0.3,
   173	        height: 0.4,
   174	        theta: 0,
   175	        asset: { url: "http://img.test/query.jpg" },
   176	        encounter: { id: "enc-query", locationId: "loc-1" },
   177	        individual: { id: "ind-query", displayName: "Luna" },
   178	      },
   179	      prospects: {
   180	        annot: [{ annotId: "a1", score: 0.9 }],
   181	        indiv: [{ individualId: "i1", score: 0.85 }],
   182	      },
   183	    },
   184	    children: [],

codex
**Primary Finding**
The likely root cause is the API, not the prospect JSON keys.

`GET /api/v3/tasks/{taskId}/match-results` exists and the React app calls it correctly from [matchResultsStore.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:424). But [Task.matchResultsJson](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:837) only serializes an existing `MatchResult` inside `if (methodInfo != null)`. `methodInfo` is null unless task params contain `ibeis.identification` as a JSON object, via [getIdentificationMethodInfo](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:744).

The v2 ml-service path creates/passes match config transiently, but does not persist it as `ibeis.identification` on the v2 match task/subtasks. `runMatchProspects` creates `matchTask = new Task(parent)` and only adds `mlServiceV2Match` before calling `Embedding.findMatchProspects` with the local `matchConfig` [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:518). `Embedding.findMatchProspects` then creates leaf subtasks and persists `MatchResult`s correctly [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:288).

So the DB can be 100% correct while the API silently omits `matchResults` from the JSON tree. Testing the subTask directly would still fail if that subTask inherited params without `ibeis.identification`.

Concrete confirmation: curl the API and look for `matchResultsRoot.children[*].children[*].matchResults`. If the task node has `statusOverall: "completed"` and `__taskAnnotations` but no `matchResults`, this is the bug.

**React/API Contract**
The frontend expects this shape:

```js
matchResultsRoot.children[].matchResults.prospects.annot[]
matchResultsRoot.children[].matchResults.prospects.indiv[]
```

Each prospect row must have `annotation`; the inspection image is top-level `asset`.

That matches the backend serializer. [MatchResult.prospectsForApiGet](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:559) groups by score type, and [MatchResultProspect.jsonForApiGet](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:82) emits `{ annotation, score, asset }`.

So I do not think the raw prospect key names are the main mismatch.

**Secondary UI Bug**
Even after the API emits the MRs, the page can still look empty for vector results that only have `annot` prospects.

`MatchProspectTable` renders rows only when `candidate.annotation` exists [MatchProspectTable.jsx](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:201). The store starts in `individual` mode and only switches to `image` if `_indivResults.length === 0` [matchResultsStore.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:133). But `getAllIndiv` adds placeholder rows for completed tasks with empty indiv prospects [helperFunctions.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/helperFunctions.js:73), so `_indivResults.length` can be nonzero while there are no displayable individual rows.

Vector `MatchResult` creation can legitimately produce only `annot` prospects when candidate annotations have no individual [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:223). The existing test even documents this with “FIXME someday we need to figure out indiv-vector-search” [MatchResultTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/MatchResultTest.java:91).

**Recommended Fix Direction**
Backend: decouple existing `MatchResult` serialization from `methodInfo`. Always serialize an existing `getLatestMatchResult()` for the task; keep legacy log-based generation gated to identification-like tasks. Also persist v2 `matchConfig`/method metadata onto the match task params so the UI can display a useful method label.

Frontend: choose the default view based on displayable prospect rows, not placeholder rows. If `annot` has rows and `indiv` does not, auto-select `image`.

Tests: add a backend test where a v2 leaf task has a persisted `MatchResult` but no `ibeis.identification`, and assert `matchResults` is still present. Add a frontend fixture using real `{ annotation, score, asset }` prospects; current tests mock `MatchProspectTable` and use fake `{ annotId }` / `{ individualId }` rows, so they miss this contract.

**Other**
Relevant frontend/API files currently show `i/lf w/crlf` from `git ls-files --eol`, including `MatchResults.jsx`, `matchResultsStore.js`, `helperFunctions.js`, `MatchProspectTable.jsx`, `GenericObject.java`, and `Embedding.java`. I did not write to any files.
tokens used
184,635
**Primary Finding**
The likely root cause is the API, not the prospect JSON keys.

`GET /api/v3/tasks/{taskId}/match-results` exists and the React app calls it correctly from [matchResultsStore.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:424). But [Task.matchResultsJson](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:837) only serializes an existing `MatchResult` inside `if (methodInfo != null)`. `methodInfo` is null unless task params contain `ibeis.identification` as a JSON object, via [getIdentificationMethodInfo](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/Task.java:744).

The v2 ml-service path creates/passes match config transiently, but does not persist it as `ibeis.identification` on the v2 match task/subtasks. `runMatchProspects` creates `matchTask = new Task(parent)` and only adds `mlServiceV2Match` before calling `Embedding.findMatchProspects` with the local `matchConfig` [MlServiceProcessor.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MlServiceProcessor.java:518). `Embedding.findMatchProspects` then creates leaf subtasks and persists `MatchResult`s correctly [Embedding.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/Embedding.java:288).

So the DB can be 100% correct while the API silently omits `matchResults` from the JSON tree. Testing the subTask directly would still fail if that subTask inherited params without `ibeis.identification`.

Concrete confirmation: curl the API and look for `matchResultsRoot.children[*].children[*].matchResults`. If the task node has `statusOverall: "completed"` and `__taskAnnotations` but no `matchResults`, this is the bug.

**React/API Contract**
The frontend expects this shape:

```js
matchResultsRoot.children[].matchResults.prospects.annot[]
matchResultsRoot.children[].matchResults.prospects.indiv[]
```

Each prospect row must have `annotation`; the inspection image is top-level `asset`.

That matches the backend serializer. [MatchResult.prospectsForApiGet](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:559) groups by score type, and [MatchResultProspect.jsonForApiGet](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResultProspect.java:82) emits `{ annotation, score, asset }`.

So I do not think the raw prospect key names are the main mismatch.

**Secondary UI Bug**
Even after the API emits the MRs, the page can still look empty for vector results that only have `annot` prospects.

`MatchProspectTable` renders rows only when `candidate.annotation` exists [MatchProspectTable.jsx](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:201). The store starts in `individual` mode and only switches to `image` if `_indivResults.length === 0` [matchResultsStore.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:133). But `getAllIndiv` adds placeholder rows for completed tasks with empty indiv prospects [helperFunctions.js](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/helperFunctions.js:73), so `_indivResults.length` can be nonzero while there are no displayable individual rows.

Vector `MatchResult` creation can legitimately produce only `annot` prospects when candidate annotations have no individual [MatchResult.java](/mnt/c/Wildbook-clean2/src/main/java/org/ecocean/ia/MatchResult.java:223). The existing test even documents this with “FIXME someday we need to figure out indiv-vector-search” [MatchResultTest.java](/mnt/c/Wildbook-clean2/src/test/java/org/ecocean/MatchResultTest.java:91).

**Recommended Fix Direction**
Backend: decouple existing `MatchResult` serialization from `methodInfo`. Always serialize an existing `getLatestMatchResult()` for the task; keep legacy log-based generation gated to identification-like tasks. Also persist v2 `matchConfig`/method metadata onto the match task params so the UI can display a useful method label.

Frontend: choose the default view based on displayable prospect rows, not placeholder rows. If `annot` has rows and `indiv` does not, auto-select `image`.

Tests: add a backend test where a v2 leaf task has a persisted `MatchResult` but no `ibeis.identification`, and assert `matchResults` is still present. Add a frontend fixture using real `{ annotation, score, asset }` prospects; current tests mock `MatchProspectTable` and use fake `{ annotId }` / `{ individualId }` rows, so they miss this contract.

**Other**
Relevant frontend/API files currently show `i/lf w/crlf` from `git ls-files --eol`, including `MatchResults.jsx`, `matchResultsStore.js`, `helperFunctions.js`, `MatchProspectTable.jsx`, `GenericObject.java`, and `Embedding.java`. I did not write to any files.
