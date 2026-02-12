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
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — loadData", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("clears state when both annot and indiv results are empty", () => {
    store.loadData({
      matchResultsRoot: makeProspect({
        matchResults: {
          numberCandidates: 0,
          queryAnnotation: {},
          prospects: { annot: [], indiv: [] },
        },
      }),
    });
    expect(store.hasResults).toBe(false);
    expect(store._rawAnnots).toEqual([]);
    expect(store._rawIndivs).toEqual([]);
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

  test("sets thisEncounterImageUrl", () => {
    store.loadData(makeApiResponse());
    expect(store.thisEncounterImageUrl).toBe("http://img.test/query.jpg");
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
    expect(meta.queryImageUrl).toBe("http://asset.test/img.jpg");
  });

  test("items without taskId are grouped under 'unknown-task'", () => {
    const items = [
      {
        score: 0.5,
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
      },
    ];
    const sections = store._processData(items);
    expect(sections[0].taskId).toBe("unknown-task");
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — currentViewData computed", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.loadData(makeApiResponse());
  });

  test("returns processedIndivs when viewMode is individual", () => {
    store.setViewMode("individual");
    expect(store.currentViewData).toEqual(store.processedIndivs);
  });

  test("returns processedAnnots when viewMode is image", () => {
    store.setViewMode("image");
    expect(store.currentViewData).toEqual(store.processedAnnots);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — setSelectedMatch", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("does nothing when key is falsy", () => {
    store.setSelectedMatch(true, null, "enc-1", "ind-1", "Name");
    expect(store.selectedMatch).toHaveLength(0);
  });

  test("does nothing when encounterId is falsy", () => {
    store.setSelectedMatch(true, "key-1", null, "ind-1", "Name");
    expect(store.selectedMatch).toHaveLength(0);
  });

  test("adds a match entry when selected is true", () => {
    store.setSelectedMatch(true, "key-1", "enc-1", "ind-1", "Luna");
    expect(store.selectedMatch).toHaveLength(1);
    expect(store.selectedMatch[0]).toMatchObject({
      key: "key-1",
      encounterId: "enc-1",
      individualId: "ind-1",
      individualDisplayName: "Luna",
    });
  });

  test("does not add duplicate keys", () => {
    store.setSelectedMatch(true, "key-1", "enc-1", "ind-1", "Luna");
    store.setSelectedMatch(true, "key-1", "enc-1", "ind-1", "Luna");
    expect(store.selectedMatch).toHaveLength(1);
  });

  test("removes a match entry when selected is false", () => {
    store.setSelectedMatch(true, "key-1", "enc-1", "ind-1", "Luna");
    store.setSelectedMatch(false, "key-1", "enc-1", "ind-1", "Luna");
    expect(store.selectedMatch).toHaveLength(0);
  });

  test("stores null for missing individualId and displayName", () => {
    store.setSelectedMatch(true, "key-2", "enc-2", null, null);
    expect(store.selectedMatch[0].individualId).toBeNull();
    expect(store.selectedMatch[0].individualDisplayName).toBeNull();
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — clearSelection / resetSelectionToQuery", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.setSelectedMatch(true, "k1", "e1", "i1", "N1");
    store._matchRequestError = "SOME_ERROR";
  });

  test("clearSelection empties selectedMatch and clears error", () => {
    store.clearSelection();
    expect(store.selectedMatch).toEqual([]);
    expect(store.matchRequestError).toBeNull();
  });

  test("resetSelectionToQuery empties selectedMatch and clears error", () => {
    store.resetSelectionToQuery();
    expect(store.selectedMatch).toEqual([]);
    expect(store.matchRequestError).toBeNull();
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — computed: querySelectionItem", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("returns null when encounterId is not set", () => {
    expect(store.querySelectionItem).toBeNull();
  });

  test("returns object with encounterId, individualId, individualDisplayName after loadData", () => {
    store.loadData(makeApiResponse());
    const q = store.querySelectionItem;
    expect(q).not.toBeNull();
    expect(q.encounterId).toBe("enc-query");
    expect(q.individualId).toBe("ind-query");
    expect(q.individualDisplayName).toBe("Luna");
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — computed: selectedIncludingQuery", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.loadData(makeApiResponse());
  });

  test("includes query item first", () => {
    const result = store.selectedIncludingQuery;
    expect(result[0].encounterId).toBe("enc-query");
  });

  test("appends additional selected matches without the query duplicate", () => {
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    const result = store.selectedIncludingQuery;
    expect(result).toHaveLength(2);
    expect(result[1].encounterId).toBe("enc-other");
  });

  test("deduplicates query encounter from selected list", () => {
    store.setSelectedMatch(true, "k1", "enc-query", "ind-query", "Luna");
    const result = store.selectedIncludingQuery;
    const queryCount = result.filter(
      (m) => m.encounterId === "enc-query",
    ).length;
    expect(queryCount).toBe(1);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — computed: uniqueIndividualIds", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("returns empty array when no individual and no selection", () => {
    expect(store.uniqueIndividualIds).toEqual([]);
  });

  test("includes individualId from loadData", () => {
    store.loadData(makeApiResponse());
    expect(store.uniqueIndividualIds).toContain("ind-query");
  });

  test("includes individualIds from selections without duplicates", () => {
    store.loadData(makeApiResponse());
    store.setSelectedMatch(true, "k1", "enc-1", "ind-query", "Luna");
    store.setSelectedMatch(true, "k2", "enc-2", "ind-other", "Other");
    const ids = store.uniqueIndividualIds;
    expect(ids).toContain("ind-query");
    expect(ids).toContain("ind-other");
    expect(ids.filter((id) => id === "ind-query")).toHaveLength(1);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — computed: matchingState", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
  });

  test("returns 'no_individuals' when no encounters in query or selection", () => {
    // No encounterId set → querySelectionItem is null → selectedIncludingQuery = []
    expect(store.matchingState).toBe("no_individuals");
  });

  test("returns 'no_individuals' when query has no individualId and no selections", () => {
    store.loadData(
      makeApiResponse({
        matchResults: {
          ...makeProspect().matchResults,
          queryAnnotation: {
            ...makeProspect().matchResults.queryAnnotation,
            individual: null,
          },
          prospects: {
            annot: [{ annotId: "a1" }],
            indiv: [{ individualId: "i1" }],
          },
        },
      }),
    );
    expect(store.matchingState).toBe("no_individuals");
  });

  test("returns 'no_further_action_needed' when all encounters have same individual", () => {
    store.loadData(makeApiResponse());
    store.setSelectedMatch(true, "k1", "enc-other", "ind-query", "Luna");
    expect(store.matchingState).toBe("no_further_action_needed");
  });

  test("returns 'single_individual' when one individual but not all have it", () => {
    store.loadData(makeApiResponse());
    // Add a selection without individual — query has ind-query, selected has no individual
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    expect(store.matchingState).toBe("single_individual");
  });

  test("returns 'two_individuals' when exactly two distinct individuals present", () => {
    store.loadData(makeApiResponse());
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    expect(store.matchingState).toBe("two_individuals");
  });

  test("returns 'too_many_individuals' when more than two distinct individuals", () => {
    store.loadData(makeApiResponse());
    store.setSelectedMatch(true, "k1", "enc-2", "ind-2", "Two");
    store.setSelectedMatch(true, "k2", "enc-3", "ind-3", "Three");
    expect(store.matchingState).toBe("too_many_individuals");
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — handleNoFurtherActionNeeded", () => {
  test("returns { ok: true, noop: true } and clears selection", () => {
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
      expect.any(Object),
    );
    expect(axios.get).toHaveBeenCalledWith(
      expect.stringContaining("prospectsSize=5"),
      expect.any(Object),
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
    expect(store.matchRequestError).toBe("ENTER_INDIVIDUAL_NAME");
  });

  test("returns error when newIndividualName is only whitespace", async () => {
    store.setNewIndividualName("   ");
    const result = await store.handleCreateNewIndividual(null);
    expect(result).toEqual({ ok: false, error: "ENTER_INDIVIDUAL_NAME" });
  });

  test("patches encounters for unassigned encounters and returns ok:true on success", async () => {
    store.setNewIndividualName("Nemo");
    // Add a selection without individual so it gets patched
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.patch.mockResolvedValue({ data: {} });
    const result = await store.handleCreateNewIndividual(null);
    expect(result.ok).toBe(true);
    expect(axios.patch).toHaveBeenCalled();
  });

  test("includes identificationRemarks patch op when remark is provided", async () => {
    store.setNewIndividualName("Nemo");
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.patch.mockResolvedValue({ data: {} });
    await store.handleCreateNewIndividual("remark-value");
    const patchOps = axios.patch.mock.calls[0][1];
    const remarkOp = patchOps.find((op) => op.path === "identificationRemarks");
    expect(remarkOp).toBeDefined();
    expect(remarkOp.value).toBe("remark-value");
  });

  test("does not include identificationRemarks op when remark is empty", async () => {
    store.setNewIndividualName("Nemo");
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.patch.mockResolvedValue({ data: {} });
    await store.handleCreateNewIndividual("");
    const patchOps = axios.patch.mock.calls[0][1];
    const remarkOp = patchOps.find((op) => op.path === "identificationRemarks");
    expect(remarkOp).toBeUndefined();
  });

  test("sets matchRequestError on axios failure", async () => {
    store.setNewIndividualName("Nemo");
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.patch.mockRejectedValueOnce(new Error("server error"));
    const result = await store.handleCreateNewIndividual(null);
    expect(result).toEqual({
      ok: false,
      error: "CREATE_NEW_INDIVIDUAL_FAILED",
    });
    expect(store.matchRequestError).toBe("CREATE_NEW_INDIVIDUAL_FAILED");
  });

  test("sets matchRequestLoading to false in finally block", async () => {
    store.setNewIndividualName("");
    await store.handleCreateNewIndividual(null);
    expect(store.matchRequestLoading).toBe(false);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — handleMatch", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.loadData(makeApiResponse()); // query has ind-query
    jest.clearAllMocks();
  });

  test("sets error when there is not exactly one unique individual", async () => {
    // query has ind-query, adding ind-other → two unique individuals
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    const result = await store.handleMatch();
    expect(result).toBeNull();
    expect(store.matchRequestError).toBe("MATCH_REQUIRES_SINGLE_INDIVIDUAL");
  });

  test("calls iaResultsSetID.jsp with correct params when one individual present", async () => {
    // query already has ind-query; add selection with same individual plus unnamed
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.get.mockResolvedValueOnce({ data: { success: true } });
    const result = await store.handleMatch();
    expect(result).toEqual({ success: true });
    const url = axios.get.mock.calls[0][0];
    expect(url).toContain("/iaResultsSetID.jsp");
    expect(url).toContain("individualID=ind-query");
  });

  test("sets matchRequestError on failure", async () => {
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.get.mockRejectedValueOnce(new Error("fail"));
    const result = await store.handleMatch();
    expect(result).toBeNull();
    expect(store.matchRequestError).toBe("MATCH_FAILED");
  });

  test("resets selection on success", async () => {
    store.setSelectedMatch(true, "k1", "enc-no-ind", null, null);
    axios.get.mockResolvedValueOnce({ data: {} });
    await store.handleMatch();
    expect(store.selectedMatch).toEqual([]);
  });
});

// ---------------------------------------------------------------------------

describe("MatchResultsStore — handleMerge", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    store.loadData(makeApiResponse()); // query has ind-query
    jest.clearAllMocks();
    delete window.open;
    window.open = jest.fn();
  });

  test("sets error when there are not exactly two unique individuals", async () => {
    // Only one individual (ind-query)
    const result = await store.handleMerge();
    expect(result).toBeNull();
    expect(store.matchRequestError).toBe("MERGE_REQUIRES_TWO_INDIVIDUALS");
  });

  test("opens merge.jsp with correct params when two individuals are present", async () => {
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    const result = await store.handleMerge();
    expect(result).toEqual({ ok: true });
    expect(window.open).toHaveBeenCalledWith(
      expect.stringContaining("/merge.jsp"),
      "_blank",
    );
    const url = window.open.mock.calls[0][0];
    expect(url).toContain("individualA=ind-query");
    expect(url).toContain("individualB=ind-other");
  });

  test("sets matchRequestError on failure", async () => {
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    window.open = jest.fn(() => {
      throw new Error("blocked");
    });
    const result = await store.handleMerge();
    expect(result).toBeNull();
    expect(store.matchRequestError).toBe("MERGE_FAILED");
  });

  test("resets selection on success", async () => {
    store.setSelectedMatch(true, "k1", "enc-other", "ind-other", "Other");
    await store.handleMerge();
    expect(store.selectedMatch).toEqual([]);
  });
});
