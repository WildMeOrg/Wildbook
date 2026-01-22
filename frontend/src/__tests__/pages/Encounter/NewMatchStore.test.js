import NewMatchStore from "../../../pages/Encounter/stores/NewMatchStore";
import axios from "axios";
import { act } from "react-dom/test-utils";

jest.mock("axios");

describe("NewMatchStore", () => {
  const makeEncounterStore = (overrides = {}) => ({
    siteSettingsData: {
      iaConfig: {
        Delphinidae: [{ description: "HotSpotter" }, { description: "MiewID" }],
      },
    },
    encounterData: {
      id: "E-1",
      taxonomy: "Delphinidae",
      locationId: "LOC-1",
      mediaAssets: [
        {
          id: "ma-1",
          annotations: [
            { id: "ann-1", encounterId: "E-1" },
            { id: "ann-2", encounterId: "OTHERS" },
          ],
        },
      ],
    },
    locationIdOptions: [
      {
        title: "LOC-1",
        value: "LOC-1",
        children: [
          { title: "LOC-1-1", value: "LOC-1-1" },
          { title: "LOC-1-2", value: "LOC-1-2" },
        ],
      },
      {
        title: "LOC-2",
        value: "LOC-2",
      },
    ],
    ...overrides,
  });

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("initializes with encounterStore and picks locationId from encounterData (reaction)", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    expect(store.locationId).toEqual(["LOC-1"]);
  });

  test("setLocationID normalizes objects and removes duplicates", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.setLocationID([{ value: "A" }, "B", { value: "A" }, "", null]);

    expect(store.locationId).toEqual(["A", "B"]);
  });

  test("annotationIds returns only annotations that belong to current encounter", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    expect(store.annotationIds).toEqual(["ann-1"]);
  });

  test("algorithmOptions is built from iaConfig based on encounter taxonomy", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    expect(store.algorithmOptions).toEqual([
      { label: "HotSpotter", value: "HotSpotter" },
      { label: "MiewID", value: "MiewID" },
    ]);
  });

  test("setAlgorithm stores selected algorithms and matchingAlgorithms maps back to full config", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.setAlgorithm(["MiewID"]);

    expect(store.algorithms).toEqual(["MiewID"]);
    expect(store.matchingAlgorithms).toEqual([{ description: "MiewID" }]);
  });

  test("handleStrictChange adds trigger and all descendants when checked", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.handleStrictChange([{ value: "LOC-1" }], [], {
      triggerValue: { value: "LOC-1" },
      checked: true,
    });

    expect(new Set(store.locationId)).toEqual(
      new Set(["LOC-1", "LOC-1-1", "LOC-1-2"]),
    );
  });

  test("handleStrictChange removes trigger and all descendants when unchecked", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.handleStrictChange([{ value: "LOC-1" }], [], {
      triggerValue: { value: "LOC-1" },
      checked: true,
    });
    expect(new Set(store.locationId)).toEqual(
      new Set(["LOC-1", "LOC-1-1", "LOC-1-2"]),
    );

    store.handleStrictChange([], [], {
      triggerValue: { value: "LOC-1" },
      checked: false,
    });

    expect(store.locationId).toEqual([]);
  });

  test("buildNewMatchPayload posts correct payload without owner / location filter", async () => {
    const encounterStore = {
      siteSettingsData: { iaConfig: {} },
      encounterData: {
        id: "E-1",
        taxonomy: "Delphinidae",
        mediaAssets: [
          {
            id: "ma-1",
            annotations: [{ id: "ann-1", encounterId: "E-1" }],
          },
        ],
      },
      locationIdOptions: [],
    };
    const store = new NewMatchStore(encounterStore);

    axios.post.mockResolvedValueOnce({ status: 200, data: { taskId: "t-1" } });

    await store.buildNewMatchPayload();

    expect(axios.post).toHaveBeenCalledWith(
      "/ia",
      {
        v2: true,
        taskParameters: {
          matchingSetFilter: {},
          matchingAlgorithms: [],
        },
        annotationIds: ["ann-1"],
        fastlane: true,
      },
      { headers: { "Content-Type": "application/json" } },
    );
  });

  test("buildNewMatchPayload includes owner=mydata and locationIds when set", async () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.setAlgorithm(["HotSpotter"]);
    store.setLocationID(["LOC-2"]);
    store.setOwner("mydata");

    axios.post.mockResolvedValueOnce({ status: 200, data: { taskId: "t-2" } });

    await store.buildNewMatchPayload();

    expect(axios.post).toHaveBeenCalledWith(
      "/ia",
      {
        v2: true,
        taskParameters: {
          matchingSetFilter: {
            owner: ["me"],
            locationIds: ["LOC-2"],
          },
          matchingAlgorithms: [{ description: "HotSpotter" }],
        },
        annotationIds: ["ann-1"],
        fastlane: true,
      },
      { headers: { "Content-Type": "application/json" } },
    );
  });

  test("setLocationID can accept already expanded ids (no double nesting)", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.setLocationID(["LOC-1", "LOC-1-1", "LOC-1-2"]);
    expect(store.locationId).toEqual(["LOC-1", "LOC-1-1", "LOC-1-2"]);
  });

  test("reaction should not overwrite locationId if user already picked something", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    expect(store.locationId).toEqual(["LOC-1"]);

    store.setLocationID(["USER-LOC"]);
    act(() => {
      encounterStore.encounterData.locationId = "SHOULD-NOT-OVERRIDE";
    });

    expect(store.locationId).toEqual(["USER-LOC"]);
  });
});
