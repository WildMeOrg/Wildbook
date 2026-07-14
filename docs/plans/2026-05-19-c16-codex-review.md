OpenAI Codex v0.130.0
--------
workdir: /mnt/c/Wildbook-clean2
model: gpt-5.5
provider: openai
approval: never
sandbox: workspace-write [workdir, /tmp, /home/jason/.codex/memories]
reasoning effort: xhigh
reasoning summaries: none
session id: 019e4183-4589-7f33-baa8-e66a6e99ea1d
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

# Codex code-review: C16 — React default-view picks tab with real prospects

Codex's secondary finding from the RCA: even after C15 fixes the
API to return matchResults, the React page renders empty because
the store defaults to the "individual" view but the typical fresh-
import case has only `annot` prospects (no individuals yet on new
animals).

Confirmed end-to-end with the new API response showing
prospects.annot[2] but no prospects.indiv. The user sees the
query annotation render on the left (working), but the right side
shows the individual tab's placeholder row instead of the image
tab's real annot prospects.

## Fix

Replace `_annotResults.length === 0` / `_indivResults.length === 0`
with `.some(r => r.hasResults)`. The `hasResults` field is already
set by getAllAnnots/getAllIndiv in helperFunctions.js — true when
a row is a real prospect, false (or absent) when it's a placeholder
row added for terminal tasks with empty prospect lists.

## Diff

diff --git a/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js b/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
index a08cb46dc..6529b3703 100644
--- a/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
+++ b/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
@@ -130,10 +130,22 @@ export default class MatchResultsStore {
       return;
     }
 
-    if (!this._annotResults || this._annotResults.length === 0) {
+    // Default-view selection. Use hasResults (true when a row carries
+    // real prospect data) instead of raw array length, because
+    // getAllAnnots / getAllIndiv add a placeholder row for terminal
+    // tasks whose prospect list is empty. With raw length, a fresh
+    // import that produced only annot prospects (typical when newly
+    // imported animals have no individual yet) would land on the
+    // "individual" tab and display only the placeholder, hiding the
+    // real annot prospects on the "image" tab.
+    // (Empty-match-prospects design Track 2 C16, per Codex RCA
+    // secondary finding.)
+    const annotHasResults = (this._annotResults || []).some((r) => r?.hasResults);
+    const indivHasResults = (this._indivResults || []).some((r) => r?.hasResults);
+    if (!annotHasResults) {
       this._viewMode = "individual";
     }
-    if (!this._indivResults || this._indivResults.length === 0) {
+    if (!indivHasResults) {
       this._viewMode = "image";
     }
 

## Review scope

- Is `r.hasResults` reliably populated? Looking at helperFunctions.js
  in the same dir, getAllAnnots/getAllIndiv set hasResults =
  safeProspects.length > 0 on the `common` object; real prospect
  rows spread that via `...common`, placeholder rows are the
  `common` object itself. So real prospects have hasResults === true,
  placeholders also have hasResults === false (since they're built
  when safeProspects is empty). Confirmed by reading the helper.
- Does this break the case where indiv DOES have real results but
  annot doesn't? annotHasResults=false → set "individual";
  indivHasResults=true → second condition doesn't fire → final
  viewMode="individual". Correct.
- Both have real results? Neither condition fires → viewMode keeps
  its initial state (from elsewhere in the store). Same as before.
- Both empty (no MR at all)? Both conditions fire; final
  viewMode="image". Same as before.
- LF-normalized after sed.

Anything else? Especially: edge cases I haven't considered, or a
better signal than `hasResults`.

**Do not write to any file.** Review-only.

warning: Codex could not find bubblewrap on PATH. Install bubblewrap with your OS package manager. See the sandbox prerequisites: https://developers.openai.com/codex/concepts/sandboxing#prerequisites. Codex will use the bundled bubblewrap in the meantime.
codex
I’ll verify the local store/helper code and the line-ending state, then review the behavior against the cases you called out. No file writes.
exec
/bin/bash -lc "sed -n '1,220p' frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git diff -- frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git ls-files --eol frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js frontend/src/pages/MatchResultsPage/helperFunctions.js' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,260p' frontend/src/pages/MatchResultsPage/helperFunctions.js" in /mnt/c/Wildbook-clean2
 succeeded in 255ms:
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

    // Default-view selection. Use hasResults (true when a row carries
    // real prospect data) instead of raw array length, because
    // getAllAnnots / getAllIndiv add a placeholder row for terminal
    // tasks whose prospect list is empty. With raw length, a fresh
    // import that produced only annot prospects (typical when newly
    // imported animals have no individual yet) would land on the
    // "individual" tab and display only the placeholder, hiding the
    // real annot prospects on the "image" tab.
    // (Empty-match-prospects design Track 2 C16, per Codex RCA
    // secondary finding.)
    const annotHasResults = (this._annotResults || []).some((r) => r?.hasResults);
    const indivHasResults = (this._indivResults || []).some((r) => r?.hasResults);
    if (!annotHasResults) {
      this._viewMode = "individual";
    }
    if (!indivHasResults) {
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

 succeeded in 288ms:
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

 succeeded in 372ms:
i/lf    w/crlf  attr/                 	frontend/src/pages/MatchResultsPage/helperFunctions.js
i/lf    w/lf    attr/                 	frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js

 succeeded in 500ms:
diff --git a/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js b/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
index a08cb46dc..6529b3703 100644
--- a/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
+++ b/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js
@@ -130,10 +130,22 @@ export default class MatchResultsStore {
       return;
     }
 
-    if (!this._annotResults || this._annotResults.length === 0) {
+    // Default-view selection. Use hasResults (true when a row carries
+    // real prospect data) instead of raw array length, because
+    // getAllAnnots / getAllIndiv add a placeholder row for terminal
+    // tasks whose prospect list is empty. With raw length, a fresh
+    // import that produced only annot prospects (typical when newly
+    // imported animals have no individual yet) would land on the
+    // "individual" tab and display only the placeholder, hiding the
+    // real annot prospects on the "image" tab.
+    // (Empty-match-prospects design Track 2 C16, per Codex RCA
+    // secondary finding.)
+    const annotHasResults = (this._annotResults || []).some((r) => r?.hasResults);
+    const indivHasResults = (this._indivResults || []).some((r) => r?.hasResults);
+    if (!annotHasResults) {
       this._viewMode = "individual";
     }
-    if (!this._indivResults || this._indivResults.length === 0) {
+    if (!indivHasResults) {
       this._viewMode = "image";
     }
 

codex
The changed store file is LF in the worktree; `helperFunctions.js` is currently `w/crlf`, but it is not the touched file in this diff. I’m checking the surrounding view-mode consumers now, because this bug depends on whether “image” and “individual” are only labels or also gate rendering.
exec
/bin/bash -lc "sed -n '220,520p' frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'rg -n "viewMode|_viewMode|hasResults|emptyStateType|getAllAnnot|getAllIndiv" frontend/src/pages/MatchResultsPage' in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "rg -n \"MatchResultsStore|loadData\\(|getAllAnnot|getAllIndiv\" frontend/src --glob '*test*' --glob '*spec*'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc 'git status --short' in /mnt/c/Wildbook-clean2
 succeeded in 260ms:
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

 succeeded in 452ms:
frontend/src/pages/MatchResultsPage/helperFunctions.js:19:  let emptyStateType = null;
frontend/src/pages/MatchResultsPage/helperFunctions.js:22:    emptyStateType = "no_candidates";
frontend/src/pages/MatchResultsPage/helperFunctions.js:24:    emptyStateType = "no_prospects";
frontend/src/pages/MatchResultsPage/helperFunctions.js:68:      hasResults: safeProspects.length > 0,
frontend/src/pages/MatchResultsPage/helperFunctions.js:69:      emptyStateType,
frontend/src/pages/MatchResultsPage/helperFunctions.js:92:export const getAllIndiv = (node, result = []) =>
frontend/src/pages/MatchResultsPage/helperFunctions.js:94:export const getAllAnnot = (node, result = []) =>
frontend/src/pages/MatchResultsPage/MatchResults.jsx:121:      {store.hasResults && store.encounterId && (
frontend/src/pages/MatchResultsPage/MatchResults.jsx:131:      {store.hasResults && store.encounterId && (
frontend/src/pages/MatchResultsPage/MatchResults.jsx:182:                store.viewMode === "individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:188:                store.viewMode === "individual"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:207:                store.viewMode === "image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:213:                store.viewMode === "image"
frontend/src/pages/MatchResultsPage/MatchResults.jsx:387:                  key={`${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:388:                  id={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:389:                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:392:                    sectionId={`${store.viewMode}-${taskId}`}
frontend/src/pages/MatchResultsPage/MatchResults.jsx:406:                    emptyStateType={metadata?.emptyStateType}
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:10:  getAllAnnot,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:11:  getAllIndiv,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:18:  _viewMode = "individual"; // "individual" | "image"
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:44:  _hasResults = false;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:88:    this._annotResults = getAllAnnot(root);
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:89:    this._indivResults = getAllIndiv(root);
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:123:      this._hasResults = false;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:133:    // Default-view selection. Use hasResults (true when a row carries
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:135:    // getAllAnnots / getAllIndiv add a placeholder row for terminal
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:143:    const annotHasResults = (this._annotResults || []).some((r) => r?.hasResults);
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:144:    const indivHasResults = (this._indivResults || []).some((r) => r?.hasResults);
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:146:      this._viewMode = "individual";
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:149:      this._viewMode = "image";
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:173:    this._hasResults = this._rawAnnots.length > 0 || this._rawIndivs.length > 0;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:237:          emptyStateType: first.emptyStateType ?? null,
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:259:    this._viewMode = "individual";
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:261:    this._hasResults = false;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:282:    return this._viewMode === "individual"
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:287:  get viewMode() {
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:288:    return this._viewMode;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:359:  get hasResults() {
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:360:    return this._hasResults;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:472:        const annLen = (getAllAnnot(root) || []).length;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:473:        const indLen = (getAllIndiv(root) || []).length;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:497:    this._hasResults = results;
frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:505:    this._viewMode = mode;
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:189:  emptyStateType,
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:576:                {emptyStateType === "no_candidates" ||
frontend/src/pages/MatchResultsPage/components/MatchProspectTable.jsx:577:                emptyStateType === "no_prospects" ? (

 succeeded in 535ms:
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:1:import MatchResultsStore from "../../../pages/MatchResultsPage/stores/matchResultsStore";
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:44:describe("MatchResultsStore — initial state", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:47:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:89:describe("MatchResultsStore — setters", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:92:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:155:describe("MatchResultsStore — loadData", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:158:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:162:    store.loadData({
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:182:    store.loadData({ matchResultsRoot: null });
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:187:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:194:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:199:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:204:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:209:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:219:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:225:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:232:describe("MatchResultsStore — _processData", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:235:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:387:describe("MatchResultsStore — currentViewData computed", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:390:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:391:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:407:describe("MatchResultsStore — setSelectedMatch", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:410:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:455:describe("MatchResultsStore — clearSelection / resetSelectionToQuery", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:458:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:478:describe("MatchResultsStore — computed: querySelectionItem", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:481:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:489:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:500:describe("MatchResultsStore — computed: selectedIncludingQuery", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:503:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:504:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:531:describe("MatchResultsStore — computed: uniqueIndividualIds", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:534:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:542:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:547:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:559:describe("MatchResultsStore — computed: matchingState", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:562:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:571:    store.loadData(
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:590:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:596:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:603:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:609:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:618:describe("MatchResultsStore — handleNoFurtherActionNeeded", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:620:    const store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:630:describe("MatchResultsStore — fetchMatchResults", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:633:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:689:describe("MatchResultsStore — handleCreateNewIndividual", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:692:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:693:    store.loadData(makeApiResponse());
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:762:describe("MatchResultsStore — handleMatch", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:765:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:766:    store.loadData(makeApiResponse()); // query has ind-query
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:807:describe("MatchResultsStore — handleMerge", () => {
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:810:    store = new MatchResultsStore();
frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:811:    store.loadData(makeApiResponse()); // query has ind-query
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:2:  getAllAnnot,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:3:  getAllIndiv,
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:35:  describe("getAllAnnot", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:37:      expect(getAllAnnot(null)).toEqual([]);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:41:      expect(getAllAnnot(undefined)).toEqual([]);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:52:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:66:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:74:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:97:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:109:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:126:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:134:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:154:      expect(getAllAnnot(node)).toHaveLength(3);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:159:      const result = getAllAnnot(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:165:  describe("getAllIndiv", () => {
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:167:      expect(getAllIndiv(null)).toEqual([]);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:178:      const result = getAllIndiv(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:186:      const result = getAllIndiv(node);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:219:      const result = getAllIndiv(root);
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:233:      expect(getAllIndiv(node)[0].numberCandidates).toBe("-");
frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js:247:      const result = getAllIndiv(node);

 succeeded in 11482ms:
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
The consumer path confirms `_viewMode` directly chooses `currentViewData`, so the default matters exactly the way the RCA describes. I’m checking the existing tests next to see whether C16 has regression coverage or only relies on helper-level behavior.
exec
/bin/bash -lc "sed -n '35,130p' frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '150,235p' frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '165,225p' frontend/src/pages/MatchResultsPage/MatchResults.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '360,420p' frontend/src/pages/MatchResultsPage/MatchResults.jsx" in /mnt/c/Wildbook-clean2
 succeeded in 229ms:
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


 succeeded in 229ms:
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

 succeeded in 222ms:
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

 succeeded in 227ms:
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


exec
/bin/bash -lc "sed -n '1,150p' frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '1,80p' frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '100,145p' frontend/src/pages/MatchResultsPage/MatchResults.jsx" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "sed -n '130,260p' frontend/src/__tests__/pages/MatchResults/helperFunctions.test.js" in /mnt/c/Wildbook-clean2
 succeeded in 265ms:
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

 succeeded in 270ms:
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

 succeeded in 273ms:
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

 succeeded in 270ms:

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
The implementation path looks sound: `hasResults` is attached in the helper for both real rows and placeholders, and `common` overwrites any prospect-level collision. I’m getting exact line numbers so the review can point at the relevant code and test gap cleanly.
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/helperFunctions.js | sed -n '56,78p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js | sed -n '128,152p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js | sed -n '155,230p'" in /mnt/c/Wildbook-clean2
exec
/bin/bash -lc "nl -ba frontend/src/pages/MatchResultsPage/MatchResults.jsx | sed -n '178,216p'" in /mnt/c/Wildbook-clean2
 succeeded in 259ms:
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

 succeeded in 262ms:
   128	        this.resetSelectionToQuery();
   129	      }
   130	      return;
   131	    }
   132	
   133	    // Default-view selection. Use hasResults (true when a row carries
   134	    // real prospect data) instead of raw array length, because
   135	    // getAllAnnots / getAllIndiv add a placeholder row for terminal
   136	    // tasks whose prospect list is empty. With raw length, a fresh
   137	    // import that produced only annot prospects (typical when newly
   138	    // imported animals have no individual yet) would land on the
   139	    // "individual" tab and display only the placeholder, hiding the
   140	    // real annot prospects on the "image" tab.
   141	    // (Empty-match-prospects design Track 2 C16, per Codex RCA
   142	    // secondary finding.)
   143	    const annotHasResults = (this._annotResults || []).some((r) => r?.hasResults);
   144	    const indivHasResults = (this._indivResults || []).some((r) => r?.hasResults);
   145	    if (!annotHasResults) {
   146	      this._viewMode = "individual";
   147	    }
   148	    if (!indivHasResults) {
   149	      this._viewMode = "image";
   150	    }
   151	
   152	    const querySource = this._findBestQuerySource();

 succeeded in 238ms:
   178	            data-testid="match-results-viewmode-individual"
   179	            style={{
   180	              borderRadius: "35px",
   181	              backgroundColor:
   182	                store.viewMode === "individual"
   183	                  ? themeColor.primaryColors.primary500
   184	                  : themeColor.primaryColors.primary50,
   185	              border: "none",
   186	              padding: "5px 10px",
   187	              color:
   188	                store.viewMode === "individual"
   189	                  ? "white"
   190	                  : themeColor.primaryColors.primary500,
   191	            }}
   192	            onClick={() => {
   193	              store.setViewMode("individual");
   194	              store.resetSelectionToQuery();
   195	            }}
   196	          >
   197	            <FormattedMessage id="INDIVIDUAL_SCORE" />
   198	          </button>
   199	
   200	          <button
   201	            type="button"
   202	            id="match-results-viewmode-image"
   203	            data-testid="match-results-viewmode-image"
   204	            style={{
   205	              borderRadius: "35px",
   206	              backgroundColor:
   207	                store.viewMode === "image"
   208	                  ? themeColor.primaryColors.primary500
   209	                  : themeColor.primaryColors.primary50,
   210	              padding: "5px 10px",
   211	              border: "none",
   212	              color:
   213	                store.viewMode === "image"
   214	                  ? "white"
   215	                  : themeColor.primaryColors.primary700,
   216	            }}

 succeeded in 245ms:
   155	describe("MatchResultsStore — loadData", () => {
   156	  let store;
   157	  beforeEach(() => {
   158	    store = new MatchResultsStore();
   159	  });
   160	
   161	  test("returns task info with hasResults=false when prospects are empty but task is terminal", () => {
   162	    store.loadData({
   163	      matchResultsRoot: makeProspect({
   164	        statusOverall: "completed", // Terminal status
   165	        matchResults: {
   166	          numberCandidates: 0,
   167	          queryAnnotation: {},
   168	          prospects: { annot: [], indiv: [] },
   169	        },
   170	      }),
   171	    });
   172	    // Even with empty prospects, we get task info to show "no results" in UI
   173	    expect(store.hasResults).toBe(true);
   174	    expect(store._rawAnnots.length).toBe(1);
   175	    expect(store._rawIndivs.length).toBe(1);
   176	    // But the items should have hasResults: false
   177	    expect(store._rawAnnots[0].hasResults).toBe(false);
   178	    expect(store._rawIndivs[0].hasResults).toBe(false);
   179	  });
   180	
   181	  test("clears state when matchResultsRoot is null", () => {
   182	    store.loadData({ matchResultsRoot: null });
   183	    expect(store.hasResults).toBe(false);
   184	  });
   185	
   186	  test("sets hasResults to true and populates raw arrays on valid data", () => {
   187	    store.loadData(makeApiResponse());
   188	    expect(store.hasResults).toBe(true);
   189	    expect(store._rawIndivs.length).toBeGreaterThan(0);
   190	    expect(store._rawAnnots.length).toBeGreaterThan(0);
   191	  });
   192	
   193	  test("sets encounterId from queryAnnotation.encounter.id", () => {
   194	    store.loadData(makeApiResponse());
   195	    expect(store.encounterId).toBe("enc-query");
   196	  });
   197	
   198	  test("sets individualId from queryAnnotation.individual.id", () => {
   199	    store.loadData(makeApiResponse());
   200	    expect(store.individualId).toBe("ind-query");
   201	  });
   202	
   203	  test("sets individualDisplayName", () => {
   204	    store.loadData(makeApiResponse());
   205	    expect(store.individualDisplayName).toBe("Luna");
   206	  });
   207	
   208	  test("sets queryImageUrl in processed data metadata", () => {
   209	    store.loadData(makeApiResponse());
   210	    const sections = store.processedIndivs;
   211	    expect(sections.length).toBeGreaterThan(0);
   212	    expect(sections[0].metadata.queryImageUrl).toBe(
   213	      "http://img.test/query.jpg",
   214	    );
   215	  });
   216	
   217	  test("clears selectedMatch after loading (resetSelectionToQuery called)", () => {
   218	    store._selectedMatch = [{ key: "k1", encounterId: "e1" }];
   219	    store.loadData(makeApiResponse());
   220	    expect(store.selectedMatch).toEqual([]);
   221	  });
   222	
   223	  test("uses annot first item when viewMode is image", () => {
   224	    store.setViewMode("image");
   225	    store.loadData(makeApiResponse());
   226	    expect(store.hasResults).toBe(true);
   227	  });
   228	});
   229	
   230	// ---------------------------------------------------------------------------

codex
No correctness findings. The change at [matchResultsStore.js:143](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:143) matches the locked design: `hasResults` is populated by the helper at [helperFunctions.js:68](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/helperFunctions.js:68), and real prospect rows inherit it via `...common`, so it is a better signal than raw array length for choosing the default tab.

One test gap: the existing store tests cover terminal empty placeholders and normal data with both result types, but not the actual regression case: `annot` has real rows while `indiv` has only a placeholder. I’d add a focused `loadData` test near [matchResultsStore.test.js:161](/mnt/c/Wildbook-clean2/frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:161) asserting `store.viewMode === "image"` for that payload, plus the inverse case if you want symmetry.

Line endings: the touched store file is `i/lf w/lf`. `helperFunctions.js` still reports `i/lf w/crlf`, but it is not part of this diff. I did not run tests, per review-only scope.
tokens used
58,103
No correctness findings. The change at [matchResultsStore.js:143](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/stores/matchResultsStore.js:143) matches the locked design: `hasResults` is populated by the helper at [helperFunctions.js:68](/mnt/c/Wildbook-clean2/frontend/src/pages/MatchResultsPage/helperFunctions.js:68), and real prospect rows inherit it via `...common`, so it is a better signal than raw array length for choosing the default tab.

One test gap: the existing store tests cover terminal empty placeholders and normal data with both result types, but not the actual regression case: `annot` has real rows while `indiv` has only a placeholder. I’d add a focused `loadData` test near [matchResultsStore.test.js:161](/mnt/c/Wildbook-clean2/frontend/src/__tests__/pages/MatchResults/matchResultsStore.test.js:161) asserting `store.viewMode === "image"` for that payload, plus the inverse case if you want symmetry.

Line endings: the touched store file is `i/lf w/lf`. `helperFunctions.js` still reports `i/lf w/crlf`, but it is not part of this diff. I did not run tests, per review-only scope.
