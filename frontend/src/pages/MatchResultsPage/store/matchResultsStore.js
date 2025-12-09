import { makeAutoObservable } from "mobx";
import axios from "axios";
import { MAX_ROWS_PER_COLUMN } from "../constants";
import { MOCK_DATA1, getAllAnnot, getAllIndiv } from "../mockupdata";


export default class MatchResultsStore {
  _viewMode = "individual";
  _encounterId = "";
  _individualId = null;
  _projectName = "";
  _numResults = 2;
  _numCandidates = 0;
  _thisEncounterImageUrl = "";
  _selectedMatchImageUrlByAlgo = new Map();
  _selectedMatch = [];
  _taskId = null;
  _newIndividualName = "";
  _groupedAnnots = [];
  _groupedIndivs = [];
  _loading = true;

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
    // this.loadData();
  }

  loadData(result) {
    const annotResults1 = getAllAnnot(MOCK_DATA1.matchResultsRoot);
    const indivResults1 = getAllIndiv(MOCK_DATA1.matchResultsRoot);

    const annotResults = getAllAnnot(result.matchResultsRoot);
    const indivResults = getAllIndiv(result.matchResultsRoot);

    this._encounterId = annotResults[0].queryEncounterId || indivResults[0].queryEncounterId;
    this._individualId = annotResults[0].queryIndividualId || indivResults[0].queryIndividualId;
    this._matchDate = annotResults[0].date || indivResults[0].date;
    this._numCandidates = annotResults[0].numberCandidates || indivResults[0].numberCandidates;
    this._thisEncounterImageUrl = annotResults[0].queryEncounterImageUrl || indivResults[0].queryEncounterImageUrl;
    this._possibleMatchImageUrl = this.viewMode === "individual" ? annotResults[0].annotation?.asset?.url : indivResults[0].annotation?.asset?.url;

    const groupByAlgorithm = (data) => {
      const grouped = new Map();
      data.forEach(item => {
        const algorithm = item.algorithm;
        if (!grouped.has(algorithm)) {
          grouped.set(algorithm, []);
        }
        grouped.get(algorithm).push(item);
      });
      return grouped;
    };

    this._groupedAnnots = groupByAlgorithm(annotResults);
    this._groupedIndivs = groupByAlgorithm(indivResults);
  }

  get groupedAnnots(){
    return this._groupedAnnots;
  }
  get groupedIndivs(){
    return this._groupedIndivs;
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

  get loading(){
    return this._loading;
  }

  setLoading(loading) {
    this._loading = loading;
  }

  getSelectedMatchImageUrl(algorithmId) {
    return this._selectedMatchImageUrlByAlgo.get(algorithmId) || "";
  }

  setPreviewImageUrl(algorithmId, url) {
    this._selectedMatchImageUrlByAlgo.set(algorithmId, url);
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
    } finally{
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

  organizeMatchesIntoColumns(matches) {
    const totalMatches = matches?.length || 0;
    if (totalMatches === 0) return [];
    const columns = [];
    for (let i = 0; i < totalMatches; i += MAX_ROWS_PER_COLUMN) {
      const columnData = matches
        .slice(i, i + MAX_ROWS_PER_COLUMN)
        .map((match, index) => ({
          ...match,
          id: i + index + 1,
        }));
      columns.push(columnData);
    }
    return columns;
  }
}



