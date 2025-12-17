
import { makeAutoObservable } from "mobx";
import axios from "axios";
import { MAX_ROWS_PER_COLUMN } from "../constants";
import { getAllAnnot, getAllIndiv } from "../helperFunctions";

export default class MatchResultsStore {
  _viewMode = "individual"; // "individual" | "image"
  _encounterId = "";
  _individualId = null;
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

  // loading / result flags
  _loading = true;
  _hasResults = false;

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  // --- data loading & transformation ---

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
    this._matchDate = first.date;
    this._numCandidates = first.numberCandidates;
    this._thisEncounterImageUrl = first.queryEncounterImageUrl;
    this._possibleMatchImageUrl = first.annotation?.asset?.url ?? "";

    this._rawAnnots = annotResults;
    this._rawIndivs = indivResults;
    this._hasResults = true;
  }

  /**
   * Normalize raw prospect list into:
   * - grouped by algorithm
   * - split into columns with displayIndex
   */
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

  get hasResults() {
    return this._hasResults;
  }

  get newIndividualName() {
    return this._newIndividualName;
  }

  get taskId() {
    return this._taskId;
  }

  // --- async actions ---

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
      // for now: just log and fall back to "no results" state
    } finally {
      this.setLoading(false);
    }
  }

  // --- simple setters / UI actions ---

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

  // --- selection state (for bottom bar logic) ---

  get selectedMatch() {
    return this._selectedMatch;
  }

  /**
   * Track which candidates the user has selected, by encounter + individual id.
   */
  setSelectedMatch(selected, encounterId, individualId) {
    if (selected) {
      this._selectedMatch = [...this._selectedMatch, { encounterId, individualId }];
    } else {
      this._selectedMatch = this._selectedMatch.filter(
        (data) => data.encounterId !== encounterId,
      );
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

  /**
   * High-level matching state used by bottom bar
   * (e.g. merge / link / create new individual).
   */
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
