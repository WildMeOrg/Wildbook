
import { makeAutoObservable } from "mobx";
import axios from "axios";
import { MAX_ROWS_PER_COLUMN } from "../constants";
import { getAllAnnot, getAllIndiv } from "../helperFunctions";

export default class MatchResultsStore {
  _viewMode = "individual"; // "individual" | "image"
  _encounterId = "";
  _individualId = null;
  _individualDisplayName = null;
  _projectName = "";
  _numResults = 12;
  _numCandidates = 0;
  _matchDate = null;
  _thisEncounterImageUrl = "";
  _possibleMatchImageUrl = "";
  _selectedMatchImageUrlByAlgo = new Map();
  _selectedMatch = [];
  _taskId = null;
  _newIndividualName = "";

  // raw data from API, before grouping / processing
  _rawAnnots = [];
  _rawIndivs = [];

  _loading = true;
  _matchRequestLoading = false;
  _matchRequestError = null;
  _hasResults = false;

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }


  loadData(result) {
    const annotResults = getAllAnnot(result.matchResultsRoot);
    const indivResults = getAllIndiv(result.matchResultsRoot);

    // safety: there might be no results at all
    if ((!annotResults || annotResults.length === 0) &&
      (!indivResults || indivResults.length === 0)) {
      this._rawAnnots = [];
      this._rawIndivs = [];
      this._encounterId = null;
      this._individualId = null;
      this._individualDisplayName = null;
      this._thisEncounterImageUrl = "";
      this._possibleMatchImageUrl = "";
      this._numCandidates = 0;
      this._matchDate = null;
      this._hasResults = false;
      return;
    }

    const first = annotResults[0] ?? indivResults[0];

    this._encounterId = first.queryEncounterId;
    this._individualId = first.queryIndividualId;
    this._individualDisplayName = first.queryIndividualDisplayName;
    this._matchDate = first.date;
    this._numCandidates = first.numberCandidates;
    this._thisEncounterImageUrl = first.queryEncounterImageUrl;
    this._possibleMatchImageUrl = first.annotation?.asset?.url ?? "";

    this._rawAnnots = annotResults;
    this._rawIndivs = indivResults;
    this._hasResults = true;
  }

  _processData(rawData) {
    // 1. filter by project name if set
    const filtered = this._projectName
      ? rawData.filter((item) => item.projectName === this._projectName)
      : rawData;

    // 2. group by algorithm
    const grouped = new Map();
    filtered.forEach((item) => {
      const algorithm = item.algorithm;
      if (!grouped.has(algorithm)) {
        grouped.set(algorithm, []);
      }
      grouped.get(algorithm).push(item);
    });

    // 3. organize into columns with metadata per algorithm
    const organized = new Map();
    for (const [algorithm, data] of grouped) {
      const columns = [];
      for (let i = 0; i < data.length; i += MAX_ROWS_PER_COLUMN) {
        const columnData = data
          .slice(i, i + MAX_ROWS_PER_COLUMN)
          .map((match, index) => ({
            ...match,
            displayIndex: i + index + 1,
          }));
        columns.push(columnData);
      }
      organized.set(algorithm, {
        columns,
        metadata: {
          numCandidates: data[0].numberCandidates,
          date: data[0].date,
          queryImageUrl: data[0].queryEncounterImageAsset?.url,
          methodName: data[0].methodName,
          methodDescription: data[0].methodDescription,
          taskStatus: data[0].taskStatus,
          taskStatusOverall: data[0].taskStatusOverall,
        },
      });
    }
    return organized;
  }

  // --- computed data for UI ---

  get processedAnnots() {
    return this._processData(this._rawAnnots);
  }

  get processedIndivs() {
    return this._processData(this._rawIndivs);
  }

  get currentViewData() {
    return this._viewMode === "individual"
      ? this.processedIndivs
      : this.processedAnnots;
  }

  get viewMode() {
    return this._viewMode;
  }

  get encounterId() {
    return this._encounterId;
  }

  get individualId() {
    return this._individualId;
  }

  get individualDisplayName() {
    return this._individualDisplayName;
  }

  get projectName() {
    return this._projectName;
  }

  get numResults() {
    return this._numResults;
  }

  get numCandidates() {
    return this._numCandidates;
  }

  get matchDate() {
    return this._matchDate;
  }

  get thisEncounterImageUrl() {
    return this._thisEncounterImageUrl;
  }

  get possibleMatchImageUrl() {
    return this._possibleMatchImageUrl;
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

  // actions

  async fetchMatchResults() {
    this.setLoading(true);
    this._hasResults = false;
    try {
      const result = await axios.get(
        `/api/v3/tasks/${this._taskId}/match-results?prospectsSize=${this.numResults}`,
      );
      this.loadData(result.data);
    } catch (e) {
      console.error(e);
    } finally {
      this.setLoading(false);
    }
  }

  // setters and actions 

  setLoading(loading) {
    this._loading = loading;
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

  setProjectName(name) {
    this._projectName = name;
  }

  setNewIndividualName(name) {
    this._newIndividualName = name;
  }

  get selectedMatch() {
    return this._selectedMatch;
  }

  setSelectedMatch(selected, encounterId, individualId) {
    if (selected) {
      this._selectedMatch = [...this._selectedMatch, { encounterId, individualId }];
    } else {
      this._selectedMatch = this._selectedMatch.filter(
        (data) => data.encounterId !== encounterId,
      );
    }
  }

  // merge functions


  //no match
  async handleConfirmNoMatch() {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const newName = (this._newIndividualName || "").trim();
      if (!newName) {
        this._matchRequestError = "ENTER_INDIVIDUAL_NAME";
        return null;
      }

      const selectedEncounterIds = Array.from(
        new Set((this._selectedMatch || []).map((m) => m?.encounterId).filter(Boolean)),
      )
      const allEncountedIds = selectedEncounterIds.push({
        encounterId: this._encounterId ?? null,
      });

      const patchOps = [{ op: "replace", path: "/individual", value: newName }];

      for (const id of allEncountedIds) {
        try {
          await axios.patch(
            `/api/v3/encounters/${encodeURIComponent(id)}`,
            patchOps,
            {
              headers: {
                "Content-Type": "application/json-patch+json",
                Accept: "application/json",
              },
            },
          );
        } catch (e) {
          console.error("patch failed:", id, e);
          this._matchRequestError = "PATCH_FAILED";
          return null;
        }
      }

      this._selectedMatch = [];
      this._newIndividualName = "";

    } finally {
      this._matchRequestLoading = false;
    }
  }

  //one individual
  async handleMatch() {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const selectedIndividualId =
        this._individualId ??
        this._selectedMatch.find((d) => d.individualId)?.individualId ??
        null;
      const selectedEncounterIds = (this._selectedMatch || [])
        .filter((d) => d?.encounterId)
        .filter((d) => (this._encounterId ? d.encounterId !== this._encounterId : true))
        .filter((d) => !d?.individualId)
        .map((d) => d.encounterId);
      const params = new URLSearchParams();
      if (this._encounterId) params.set("number", this._encounterId);
      if (this._taskId) params.set("taskId", this._taskId);
      if (selectedIndividualId) params.set("individualID", selectedIndividualId);
      selectedEncounterIds.forEach((id) => params.append("encOther", id));

      const url = `/iaResultsSetID.jsp?${params.toString()}`;

      const res = await axios.get(url, {
        headers: { Accept: "application/json" },
      })

      return res.data;
    } catch (e) {
      console.error(e);
      this._matchRequestError = e;
      return null;
    } finally {
      this._matchRequestLoading = false;
    }
  }

  //two individuals + optional encounters
  async handleMerge() {
    this._matchRequestLoading = true;
    this._matchRequestError = null;

    try {
      const selected = Array.isArray(this._selectedMatch) ? this._selectedMatch : [];

      const individualIds = selected
        .filter((d) => d?.individualId)
        .map((d) => d.individualId)
      if(this._individualId) {
        individualIds.push(this._individualId);
      }

      const uniqueIndividuals = Array.from(new Set(individualIds)).filter(Boolean);

      if (uniqueIndividuals.length !== 2) {
        this._matchRequestError = "MERGE_REQUIRES_TWO_INDIVIDUALS";
        return null;
      }

      const [individualA, individualB] = uniqueIndividuals;

      const encounterIds = selected
        .filter((d) => d?.encounterId && !d?.individualId)
        .map((d) => d.encounterId);

      const uniqueEncounterIds = Array.from(new Set(encounterIds)).filter(Boolean);

      const params = new URLSearchParams();
      params.set("individualA", individualA);
      params.set("individualB", individualB);
      uniqueEncounterIds.forEach((id) => params.append("encounterId", id));

      const url = `/merge.jsp?${params.toString()}`;
      window.open(url, "_blank");

      this._selectedMatch = [];
    } catch (e) {
      console.error(e);
      this._matchRequestError = "MERGE_FAILED";
      return null;
    } finally {
      this._matchRequestLoading = false;
    }
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

  get matchingState() {
    if (this._selectedMatch.length === 0) {
      return "no_selection";
    }

    const uniqueIds = this.uniqueIndividualIds;
    const idCount = uniqueIds.length;

    if (idCount === 0) {
      return "no_individuals";
    } else if (idCount === 1) {
      return "single_individual";
    } else if (idCount === 2) {
      return "two_individuals";
    } else {
      return "too_many_individuals";
    }
  }
}
