import { makeAutoObservable } from "mobx";
import axios from "axios";
import { MAX_ROWS_PER_COLUMN } from "../constants";
import { MOCK_DATA } from "../mockupdata";


export default class MatchResultsStore {
  _viewMode = "individual";
  _encounterId = "";
  _individualId = null; 
  _projectName = "";
  _evaluatedAt = "";
  _numResults = 12;
  _numCandidates = 0;
  _thisEncounterImageUrl = "";
  _possibleMatchImageUrl = "";
  _algorithms = [];
  _selectedMatch = [];
  _taskId = null;
  _noMatchReason = ""; 

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
    this.loadData();
  }

  loadData(result) {
    this._viewMode = MOCK_DATA.viewMode;
    this._encounterId = MOCK_DATA.encounterId;
    this._individualId = MOCK_DATA.individualId;
    this._projectName = MOCK_DATA.projectName;
    this._evaluatedAt = MOCK_DATA.evaluatedAt;
    this._numResults = MOCK_DATA.numResults;
    this._numCandidates = MOCK_DATA.numCandidates;
    this._thisEncounterImageUrl = MOCK_DATA.thisEncounterImageUrl;
    this._possibleMatchImageUrl = MOCK_DATA.possibleMatchImageUrl;
    this._algorithms = MOCK_DATA.algorithms.map((algo) => ({
      ...algo,
      matches: algo.matches.map((m) => ({ ...m })),
    }));
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

  get evaluatedAt() {
    return this._evaluatedAt;
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

  get possibleMatchImageUrl() {
    return this._possibleMatchImageUrl;
  }

  get algorithms() {
    return this._algorithms;
  }

  get noMatchReason() {
    return this._noMatchReason;
  }

  get taskId() {
    return this._taskId;
  }

  setTaskId(id) {
    this._taskId = id;
  }

  async getMatchResults() {
    try {
      const result = await axios.get();
      this.loadData(result);
    } catch (e) {
      console.log();
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

  setNoMatchReason(reason) {
    this._noMatchReason = reason;
  }

  get selectedMatch() {
    return this._selectedMatch;
  }

  setSelectedMatch(selected, encounterId, individualId) {
    if (selected) {
      this._selectedMatch.push({
        encounterId,
        individualId,
      });
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
    const totalMatches = matches.length;
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