import { makeAutoObservable } from "mobx";

const MOCK_DATA = {
  viewMode: "individual", // "individual" | "image"
  encounterId: "sdf9-sdaw-f624-4d3",
  projectName: "Giraffe Conservation Project",
  evaluatedAt: "2024/02/29 7:34 PM",
  numResults: 12,
  numCandidates: 2343,
  thisEncounterImageUrl:
    "https://images.pexels.com/photos/667205/pexels-photo-667205.jpeg",
  possibleMatchImageUrl:
    "https://images.pexels.com/photos/667205/pexels-photo-667205.jpeg",

  algorithms: [
    {
      id: "miewId",
      label: "Matches Based on MIEW ID Algorithm",
      matches: [
        {
          rank: 1,
          score: 0.8315,
          encounterId: "123",
          individualId: "TC_00124",
        },
        {
          rank: 2,
          score: 0.8315,
          encounterId: "456",
          individualId: "TC_00126",
        },
        {
          rank: 3,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00125",
        },
        {
          rank: 4,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00127",
        },
        {
          rank: 5,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00130",
        },
        {
          rank: 6,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00129",
        },
        {
          rank: 7,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00131",
        },
        {
          rank: 8,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00128",
        },
        {
          rank: 9,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00135",
        },
        {
          rank: 10,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00133",
        },
        {
          rank: 11,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00134",
        },
        {
          rank: 12,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00132",
        },
      ],
    },
    {
      id: "hotspotter",
      label: "Matches Based on Hotspotter",
      matches: [
        {
          rank: 1,
          score: 0.8315,
          encounterId: "789",
          individualId: "TC_00124",
        },
        {
          rank: 2,
          score: 0.8315,
          encounterId: "000",
          individualId: "TC_00126",
        },
        {
          rank: 3,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00125",
        },
        {
          rank: 4,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00127",
        },
        {
          rank: 5,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00130",
        },
        {
          rank: 6,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00129",
        },
        {
          rank: 7,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00131",
        },
        {
          rank: 8,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00128",
        },
        {
          rank: 9,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00135",
        },
        {
          rank: 10,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00133",
        },
        {
          rank: 11,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00134",
        },
        {
          rank: 12,
          score: 0.8315,
          encounterId: "test",
          individualId: "TC_00132",
        },
      ],
    },
  ],
};

export default class MatchResultsStore {
  viewMode = "individual";
  encounterId = "";
  projectName = "";
  evaluatedAt = "";
  numResults = 12;
  numCandidates = 0;
  thisEncounterImageUrl = "";
  possibleMatchImageUrl = "";
  algorithms = [];
  _selectedMatch = [];

  constructor() {
    makeAutoObservable(this, {}, { autoBind: true });
    this.loadMockData();
  }

  loadMockData() {
    this.viewMode = MOCK_DATA.viewMode;
    this.encounterId = MOCK_DATA.encounterId;
    this.projectName = MOCK_DATA.projectName;
    this.evaluatedAt = MOCK_DATA.evaluatedAt;
    this.numResults = MOCK_DATA.numResults;
    this.numCandidates = MOCK_DATA.numCandidates;
    this.thisEncounterImageUrl = MOCK_DATA.thisEncounterImageUrl;
    this.possibleMatchImageUrl = MOCK_DATA.possibleMatchImageUrl;
    this.algorithms = MOCK_DATA.algorithms.map((algo) => ({
      ...algo,
      matches: algo.matches.map((m) => ({ ...m })),
    }));
  }

  setViewMode(mode) {
    this.viewMode = mode;
  }

  setNumResults(n) {
    this.numResults = n;
  }

  setProjectName(name) {
    this.projectName = name;
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
      this._selectedMatch = this.selectedMatch.filter(
        (data) => data.encounterId !== encounterId,
      );
    }
  }
}
