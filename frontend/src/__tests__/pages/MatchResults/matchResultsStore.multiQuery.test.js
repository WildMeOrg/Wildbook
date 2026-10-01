import MatchResultsStore from "../../../pages/MatchResultsPage/stores/matchResultsStore";
import axios from "axios";

jest.mock("axios");

// ---------------------------------------------------------------------------
// Issue #1744: a task tree whose root is an image-wide umbrella with one
// child per annotation on the image. Each child owns a MatchResult whose
// queryAnnotation belongs to a DIFFERENT encounter, so the page shows more
// than one "This Encounter" section. Every action must follow the section
// the user selected from — never the arbitrary first section.
// ---------------------------------------------------------------------------

const querySection = ({ taskId, encId, individual, locationId, prospect }) => ({
  id: taskId,
  status: "complete",
  statusOverall: "completed",
  dateCreated: "2024-06-01",
  method: { name: "miewid", description: "MiewID" },
  matchingSetFilter: {},
  matchResults: {
    numberCandidates: 10,
    queryAnnotation: {
      id: `q-${taskId}`,
      asset: { url: `http://img.test/${taskId}.jpg` },
      encounter: { id: encId, locationId },
      individual,
    },
    prospects: {
      annot: [
        {
          annotation: {
            id: `p-${taskId}`,
            encounter: { id: prospect.encounterId },
            individual: prospect.individual,
          },
          score: 0.9,
        },
      ],
      indiv: [],
    },
  },
  children: [],
});

// Section A's query encounter already has an individual; section B's does not.
const sectionA = () =>
  querySection({
    taskId: "task-A",
    encId: "enc-A",
    individual: { id: "ind-A", displayName: "Ava" },
    locationId: "loc-A",
    prospect: { encounterId: "cand-A1", individual: null },
  });

const sectionB = () =>
  querySection({
    taskId: "task-B",
    encId: "enc-B",
    individual: null,
    locationId: "loc-B",
    prospect: { encounterId: "cand-B1", individual: null },
  });

const multiQueryResponse = () => ({
  matchResultsRoot: {
    id: "umbrella",
    dateCreated: "2024-06-01",
    children: [sectionA(), sectionB()],
  },
});

const singleQueryResponse = () => ({ matchResultsRoot: sectionB() });

describe("MatchResultsStore — multi-query trees (issue #1744)", () => {
  let store;
  beforeEach(() => {
    store = new MatchResultsStore();
    jest.clearAllMocks();
  });

  test("each section's metadata carries its own query encounter", () => {
    store.loadData(multiQueryResponse());
    const sections = store.processedAnnots;
    expect(sections.map((s) => s.taskId)).toEqual(["task-A", "task-B"]);
    expect(sections.map((s) => s.metadata.queryEncounterId)).toEqual([
      "enc-A",
      "enc-B",
    ]);
    expect(sections[0].metadata.queryIndividualId).toBe("ind-A");
    expect(sections[1].metadata.queryIndividualId).toBeNull();
  });

  test("with nothing selected a multi-query tree has no target and mutating actions refuse", async () => {
    store.loadData(multiQueryResponse());
    // no default: the first-listed section must never be named by accident
    expect(store.encounterId).toBeNull();
    expect(store.individualId).toBeNull();
    expect(store.querySelectionItem).toBeNull();
    expect(store.matchingState).toBe("no_individuals");

    store.setNewIndividualName("Zed");
    const created = await store.handleCreateNewIndividual("");
    expect(created.ok).toBe(false);
    expect(await store.handleMatch()).toBeNull();
    expect(axios.patch).not.toHaveBeenCalled();
    expect(axios.get).not.toHaveBeenCalled();
  });

  test("selecting a candidate in section B makes B's encounter the query", () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    expect(store.encounterId).toBe("enc-B");
    expect(store.individualId).toBeNull();
    expect(store.individualDisplayName).toBeNull();
    expect(store.encounterLocationId).toBe("loc-B");
    expect(store.querySelectionItem).toEqual({
      encounterId: "enc-B",
      individualId: null,
      individualDisplayName: null,
    });
    expect(store.matchingState).toBe("no_individuals");
  });

  test("handleMatch names the selected section's encounter, not the first section's", async () => {
    store.loadData(multiQueryResponse());
    // a NAMED candidate ticked in section B → CASE 3 server-side: enc-B gets ind-X
    store.setSelectedMatch(
      true,
      "task-B-k1",
      "cand-B1",
      "ind-X",
      "Xena",
      "task-B",
    );
    expect(store.matchingState).toBe("single_individual");
    axios.get.mockResolvedValueOnce({ data: { success: true } });
    await store.handleMatch();
    expect(axios.get).toHaveBeenCalledTimes(1);
    const url = axios.get.mock.calls[0][0];
    expect(url).toContain("/iaResultsSetID.jsp");
    expect(url).toContain("number=enc-B");
    expect(url).toContain("individualID=ind-X");
    expect(url).not.toContain("enc-A");
    expect(url).not.toContain("ind-A");
  });

  test("deselecting drops the query context again", () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    expect(store.encounterId).toBe("enc-B");
    store.setSelectedMatch(false, "task-B-k1", "cand-B1", null, null, "task-B");
    expect(store.encounterId).toBeNull();
    expect(store.querySelectionItem).toBeNull();
  });

  test("selections spanning two query encounters are ambiguous and every action refuses", async () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-A-k1", "cand-A1", null, null, "task-A");
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    expect(store.matchingState).toBe("multiple_query_encounters");

    expect(await store.handleMatch()).toBeNull();
    expect(store.matchRequestError).toBe("MULTIPLE_QUERY_ENCOUNTERS");
    expect(await store.handleMerge()).toBeNull();
    store.setNewIndividualName("Zed");
    const created = await store.handleCreateNewIndividual("");
    expect(created.ok).toBe(false);
    expect(axios.get).not.toHaveBeenCalled();
    expect(axios.patch).not.toHaveBeenCalled();
    // the refusal leaves the selection intact so the user can fix it
    expect(store.selectedMatch).toHaveLength(2);
  });

  test("a selection whose section is unknown is ambiguous in a multi-query tree", () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "k1", "cand-B1", null, null); // no section
    expect(store.matchingState).toBe("multiple_query_encounters");
    store.clearSelection();
    store.setSelectedMatch(true, "k2", "cand-B1", null, null, "task-gone");
    expect(store.matchingState).toBe("multiple_query_encounters");
  });

  test("a selection without section info is harmless in a single-query tree (existing contract)", () => {
    store.loadData(singleQueryResponse());
    store.setSelectedMatch(true, "k1", "cand-B1", "ind-X", "Xena");
    expect(store.encounterId).toBe("enc-B");
    expect(store.matchingState).toBe("single_individual");
  });

  test("a silent refresh that drops the selected section makes the selection ambiguous", () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    const refreshed = multiQueryResponse();
    refreshed.matchResultsRoot.children[1] = querySection({
      taskId: "task-C",
      encId: "enc-C",
      individual: null,
      locationId: "loc-C",
      prospect: { encounterId: "cand-C1", individual: null },
    });
    store.loadData(refreshed, { preserveSelection: true });
    expect(store.selectedMatch).toHaveLength(1);
    expect(store.matchingState).toBe("multiple_query_encounters");
  });

  test("a refresh that leaves only another section while B's candidate is selected refuses every action", async () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(
      true,
      "task-B-k1",
      "cand-B1",
      "ind-X",
      "Xena",
      "task-B",
    );
    // polling refresh: the tree now holds only section A (single query)
    store.loadData(
      { matchResultsRoot: sectionA() },
      { preserveSelection: true },
    );
    expect(store.selectedMatch).toHaveLength(1);
    expect(store.matchingState).toBe("multiple_query_encounters");
    expect(await store.handleMatch()).toBeNull();
    expect(await store.handleMerge()).toBeNull();
    store.setNewIndividualName("Zed");
    expect((await store.handleCreateNewIndividual("")).ok).toBe(false);
    expect(axios.get).not.toHaveBeenCalled();
    expect(axios.patch).not.toHaveBeenCalled();
  });

  test("handleCreateNewIndividual patches the selected section's encounter and reports it", async () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    store.setNewIndividualName("Zed");
    axios.patch.mockResolvedValue({
      data: { patchResults: [{ individualId: "new-uuid" }] },
    });
    const result = await store.handleCreateNewIndividual("");
    expect(result.ok).toBe(true);
    expect(result.encounterId).toBe("enc-B");
    const patched = axios.patch.mock.calls.map((c) => c[0]);
    expect(patched).toEqual(
      expect.arrayContaining([
        "/api/v3/encounters/enc-B",
        "/api/v3/encounters/cand-B1",
      ]),
    );
    expect(patched).not.toContain("/api/v3/encounters/enc-A");
  });

  test("location-based naming uses the selected section's locationId", async () => {
    store.loadData(multiQueryResponse());
    store.setSelectedMatch(true, "task-B-k1", "cand-B1", null, null, "task-B");
    store.setNewIndividualName("ignored", true);
    axios.patch.mockResolvedValue({
      data: { patchResults: [{ individualId: "new-uuid" }] },
    });
    await store.handleCreateNewIndividual("");
    const firstOps = axios.patch.mock.calls[0][1];
    expect(firstOps[0]).toEqual({
      op: "replace",
      path: "individualId",
      value: { type: "locationId", value: "loc-B" },
    });
  });
});
