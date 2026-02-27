import NewMatchStore from "../../../pages/Encounter/stores/NewMatchStore";
import axios from "axios";
import { act } from "react";

jest.mock("axios");
jest.mock("../../../utils/treeSelectionFunction", () => ({
  findNodeByValue: jest.fn((nodes, value) => {
    const dfs = (arr = []) => {
      for (const n of arr) {
        if (n.value === value) return n;
        const found = dfs(n.children || []);
        if (found) return found;
      }
      return null;
    };
    return dfs(nodes);
  }),
  getAllDescendantValues: jest.fn((node) => {
    const out = [];
    const walk = (n) => {
      (n.children || []).forEach((c) => {
        out.push(c.value);
        walk(c);
      });
    };
    walk(node || {});
    return out;
  }),
  expandIds: jest.fn((_nodes, ids) => ids || []),
}));

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
    selectedImageIndex: 0,
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

  test("annotationIds returns only annotations that belong to current encounter (selected image)", () => {
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

  test("auto-selects default algorithms when iaConfig has default=true", () => {
    const encounterStore = makeEncounterStore({
      siteSettingsData: {
        iaConfig: {
          Delphinidae: [
            { description: "HotSpotter", default: true },
            { description: "MiewID" },
            { description: "WhaleNet", default: true },
          ],
        },
      },
    });

    const store = new NewMatchStore(encounterStore);

    expect(store.algorithms).toEqual(["HotSpotter", "WhaleNet"]);
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

  test("handleStrictChange without extra.triggerValue falls back to expandIds path", () => {
    const encounterStore = makeEncounterStore();
    const store = new NewMatchStore(encounterStore);

    store.handleStrictChange([{ value: "LOC-2" }], [], {});

    expect(store.locationId).toEqual(["LOC-2"]);
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
      selectedImageIndex: 0,
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

  test("annotationIds uses selectedImageIndex when multiple media assets exist", () => {
    const encounterStore = makeEncounterStore({
      selectedImageIndex: 1,
      encounterData: {
        id: "E-1",
        taxonomy: "Delphinidae",
        locationId: "LOC-1",
        mediaAssets: [
          {
            id: "ma-1",
            annotations: [{ id: "ann-1", encounterId: "E-1" }],
          },
          {
            id: "ma-2",
            annotations: [
              { id: "ann-3", encounterId: "E-1" },
              { id: "ann-4", encounterId: "OTHER" },
            ],
          },
        ],
      },
    });

    const store = new NewMatchStore(encounterStore);

    expect(store.annotationIds).toEqual(["ann-3"]);
  });
});
