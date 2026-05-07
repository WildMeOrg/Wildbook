import { makeAutoObservable } from "mobx";
import axios from "axios";
import { toast } from "react-toastify";
import { MAX_ROWS_PER_COLUMN } from "../constants";
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
  _numResults = 12;
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
        if (this._currentRequestId !== requestId || this._taskId !== capturedTaskId) {
          return;
        }
        this.loadData(result?.data, { preserveSelection: false });
      } catch (e) {
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
        if (this._currentRequestId !== requestId || this._taskId !== capturedTaskId) {
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
          throw new Error("Failed to silently refresh match results: " + (e?.message || String(e)));
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
    this._numResults = n;
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
        axios.patch(
          `/api/v3/encounters/${encodeURIComponent(id)}`,
          patchOps,
          {
            headers: {
              "Content-Type": "application/json-patch+json",
              Accept: "application/json",
            },
          },
        ).then(
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
          failures: failures.map((f) => ({ encounterId: f.encounterId, error: f.error?.message || String(f.error) })),
        };
      }

      this.resetSelectionToQuery();
      toast.success("New individual created successfully!");
      return { ok: true, successes };
    } catch (e) {
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
    } catch (e) {
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
      params.set("individualB", individualB);
      unnamedEncounterIds.forEach((id) => params.append("encounterId", id));

      const url = `/merge.jsp?${params.toString()}`;
      window.open(url, "_blank");

      this.resetSelectionToQuery();
      toast.success("Merge page opened successfully!");
      return { ok: true };
    } catch (e) {
      this._matchRequestError = "MERGE_FAILED";
      toast.error("Failed to start merge");
      return null;
    } finally {
      this._matchRequestLoading = false;
    }
  }

  resetSelectionToQuery() {
    this._selectedMatch = [];
    this._matchRequestError = null;
  }

  get matchingState() {
    const all = this.selectedIncludingQuery;

    const uniqueIndividuals = Array.from(
      new Set(all.map((m) => m?.individualId).filter(Boolean)),
    );

    const allHaveIndividual =
      all.length > 0 && all.every((m) => m?.encounterId && m?.individualId);

    if (uniqueIndividuals.length === 0) return "no_individuals";
    if (uniqueIndividuals.length === 1) {
      return allHaveIndividual
        ? "no_further_action_needed"
        : "single_individual";
    }
    if (uniqueIndividuals.length === 2) return "two_individuals";
    return "too_many_individuals";
  }
}
