import { makeAutoObservable } from "mobx";
import axios from "axios";
import { MAX_ROWS_PER_COLUMN } from "../constants";
import { getAllAnnot, getAllIndiv } from "../helperFunctions";


export default class MatchResultsStore {
  _viewMode = "individual";
  _encounterId = "";
  _individualId = null;
  _projectName = "";
  _numResults = 12;
  _numCandidates = 0;
  _thisEncounterImageUrl = "";
  _selectedMatchImageUrlByAlgo = new Map();
  _selectedMatch = [];
  _taskId = null;
  _newIndividualName = "";
  _rawAnnots = [];
  _rawIndivs = [];
  _loading = true;

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
  }

  loadData(result) {
    const annotResults = getAllAnnot(result.matchResultsRoot);
    const indivResults = getAllIndiv(result.matchResultsRoot);

    const firstAnnot = annotResults[0];
    const firstIndiv = indivResults[0];
    const first = firstAnnot ?? firstIndiv;

    if (!first) {
      this._loading = false;
      return;
    }

    this._encounterId = annotResults[0].queryEncounterId || indivResults[0].queryEncounterId;
    this._individualId = annotResults[0].queryIndividualId || indivResults[0].queryIndividualId;
    this._matchDate = annotResults[0].date || indivResults[0].date;
    this._numCandidates = annotResults[0].numberCandidates || indivResults[0].numberCandidates;
    this._thisEncounterImageUrl = annotResults[0].queryEncounterImageUrl || indivResults[0].queryEncounterImageUrl;
    this._possibleMatchImageUrl = this.viewMode === "individual" ? annotResults[0].annotation?.asset?.url : indivResults[0].annotation?.asset?.url;

    this._rawAnnots = annotResults;
    this._rawIndivs = indivResults;
  }

  _processData(rawData) {
    // 1.filter by project name if set
    const filtered = this._projectName
      ? rawData.filter(item => item.projectName === this._projectName)
      : rawData;

    // 2. group by algorithm
    const grouped = new Map();
    filtered.forEach(item => {
      const algorithm = item.algorithm;
      if (!grouped.has(algorithm)) {
        grouped.set(algorithm, []);
      }
      grouped.get(algorithm).push(item);
    });

    // 3. organize into columns
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
        }
      });
    }
    return organized;
  }

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

  get thisEncounterImageUrl() {
    return this._thisEncounterImageUrl;
  }

  get loading() {
    return this._loading;
  }

  setLoading(loading) {
    this._loading = loading;
  }

  get newIndividualName() {
    return this._newIndividualName;
  }

  get taskId() {
    return this._taskId;
  }

  setTaskId(id) {
    this._taskId = id;
  }

  async fetchMatchResults() {
    this.setLoading(true);
    try {
      const result = await axios.get(
        `/api/v3/tasks/${this._taskId}/match-results?prospectsSize=${this.numResults}`
      );
      this.loadData(result.data);
    } catch (e) {
      console.error(e);
    } finally {
      this.setLoading(false);
    }
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

  setNewIndividualName(reason) {
    this._newIndividualName = reason;
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

  get organizedAnnotColumns() {
    const organized = new Map();
    for (const [algorithm, data] of this.filteredGroupedAnnots) {
      organized.set(algorithm, this.organizeMatchesIntoColumns(data));
    }
    return organized;
  }

  get organizedIndivColumns() {
    const organized = new Map();
    for (const [algorithm, data] of this.filteredGroupedIndivs) {
      organized.set(algorithm, this.organizeMatchesIntoColumns(data));
    }
    return organized;
  }
}



