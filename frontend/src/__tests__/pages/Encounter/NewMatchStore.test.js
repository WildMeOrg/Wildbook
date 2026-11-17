import NewMatchStore from "../../../pages/Encounter/stores/NewMatchStore";
import axios from "axios";
import { act } from "react";
import { observable, runInAction } from "mobx";
import EncounterStore from "../../../pages/Encounter/stores/EncounterStore";

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

  describe("matchers narrowed to the IA class of the selected annotations", () => {
    const FACE = {
      description: "Face matcher (vector)",
      default: true,
      _iaClasses: ["dog_face"],
    };
    const BODY = {
      description: "Body matcher (vector)",
      default: true,
      _iaClasses: ["detection", "dog+body"],
    };
    const HOTSPOTTER = {
      description: "HotSpotter",
      default: false,
      _iaClasses: ["dog_face"],
    };
    const ann = (id, iaClass, extra = {}) => ({
      id,
      encounterId: "E-1",
      iaClass,
      embeddingCounts: { miewid: 1 },
      ...extra,
    });
    // observable, so the store's reactions see later changes
    const makeDogStore = (mediaAssets, iaConfig = [FACE, BODY, HOTSPOTTER]) =>
      observable({
        siteSettingsData: { iaConfig: { "Canis familiaris": iaConfig } },
        encounterData: {
          id: "E-1",
          taxonomy: "Canis familiaris",
          mediaAssets,
        },
        selectedImageIndex: 0,
      });
    const labels = (store) => store.algorithmOptions.map((o) => o.value);

    test("offers and preselects only the matchers of the annotation's class", () => {
      const store = new NewMatchStore(
        makeDogStore([{ annotations: [ann("a1", "dog+body")] }]),
      );

      expect(labels(store)).toEqual([BODY.description]);
      expect(store.algorithms).toEqual([BODY.description]);
      expect(store.matchingAlgorithms).toEqual([BODY]);
    });

    test("ignores the classes of annotations that belong to another encounter", () => {
      const store = new NewMatchStore(
        makeDogStore([
          {
            annotations: [
              ann("a1", "dog_face"),
              ann("a2", "dog+body", { encounterId: "OTHER" }),
            ],
          },
        ]),
      );

      expect(labels(store)).toEqual([FACE.description, HOTSPOTTER.description]);
    });

    test("offers the union when the image has annotations of two classes", () => {
      const store = new NewMatchStore(
        makeDogStore([
          { annotations: [ann("a1", "dog_face"), ann("a2", "dog+body")] },
        ]),
      );

      expect(labels(store)).toEqual([
        FACE.description,
        BODY.description,
        HOTSPOTTER.description,
      ]);
    });

    test("does not narrow when the annotations carry no class", () => {
      const store = new NewMatchStore(
        makeDogStore([{ annotations: [ann("a1", null)] }]),
      );

      expect(labels(store)).toHaveLength(3);
    });

    test("does not narrow when a selected class is listed by no matcher", () => {
      // a class stored under a _save_as name with no entry of its own still
      // matches through the taxonomy defaults; hiding matchers would be wrong
      const lone = new NewMatchStore(
        makeDogStore([{ annotations: [ann("a1", "canonical")] }]),
      );
      const mixed = new NewMatchStore(
        makeDogStore([
          { annotations: [ann("a1", "canonical"), ann("a2", "dog+body")] },
        ]),
      );

      expect(labels(lone)).toHaveLength(3);
      expect(labels(mixed)).toHaveLength(3);
    });

    test("keeps matchers that carry no _iaClasses list", () => {
      const legacy = { description: "Legacy matcher" };
      const store = new NewMatchStore(
        makeDogStore(
          [{ annotations: [ann("a1", "dog+body")] }],
          [FACE, BODY, legacy],
        ),
      );

      expect(labels(store)).toEqual([BODY.description, legacy.description]);
    });

    test("switching to an image of another class drops the old choice and applies the new defaults", () => {
      const encounterStore = makeDogStore([
        { annotations: [ann("a1", "dog_face")] },
        { annotations: [ann("a2", "dog+body")] },
      ]);
      const store = new NewMatchStore(encounterStore);
      expect(store.algorithms).toEqual([FACE.description]);

      runInAction(() => {
        encounterStore.selectedImageIndex = 1;
      });

      expect(store.algorithms).toEqual([BODY.description]);
      expect(store.matchingAlgorithms).toEqual([BODY]);
    });

    test("a choice that is still offered survives a selection change, without defaults added to it", () => {
      const encounterStore = makeDogStore([
        { annotations: [ann("a1", "dog_face")] },
        { annotations: [ann("a2", "dog_face")] },
      ]);
      const store = new NewMatchStore(encounterStore);
      store.setAlgorithm([HOTSPOTTER.description]);

      runInAction(() => {
        encounterStore.selectedImageIndex = 1;
      });

      expect(store.algorithms).toEqual([HOTSPOTTER.description]);
    });

    test("a refresh with the same annotations does not re-apply defaults over a cleared selection", () => {
      const encounterStore = makeDogStore([
        { annotations: [ann("a1", "dog_face")] },
      ]);
      const store = new NewMatchStore(encounterStore);
      store.setAlgorithm([]);

      runInAction(() => {
        encounterStore.encounterData.mediaAssets = [
          { annotations: [ann("a1", "dog_face")] },
        ];
      });

      expect(store.algorithms).toEqual([]);
    });

    test("vector matchers are dropped from the selection when the annotations have no embedding", () => {
      const encounterStore = makeDogStore(
        [
          { annotations: [ann("a1", "dog_face")] },
          { annotations: [ann("a2", "dog_face", { embeddingCounts: {} })] },
        ],
        [FACE, { ...HOTSPOTTER, default: true }],
      );
      const store = new NewMatchStore(encounterStore);
      expect(store.algorithms).toEqual([
        FACE.description,
        HOTSPOTTER.description,
      ]);

      runInAction(() => {
        encounterStore.selectedImageIndex = 1;
      });

      expect(labels(store)).toEqual([HOTSPOTTER.description]);
      expect(store.algorithms).toEqual([HOTSPOTTER.description]);
    });

    test("an embedding arriving on the same annotation selects the newly offered vector default", () => {
      const encounterStore = makeDogStore(
        [{ annotations: [ann("a1", "dog_face", { embeddingCounts: {} })] }],
        [FACE],
      );
      const store = new NewMatchStore(encounterStore);
      expect(store.algorithms).toEqual([]);

      runInAction(() => {
        encounterStore.encounterData.mediaAssets = [
          { annotations: [ann("a1", "dog_face")] },
        ];
      });

      expect(store.algorithms).toEqual([FACE.description]);
    });

    test("losing the last choice with no default on offer leaves nothing selected or posted", () => {
      const encounterStore = makeDogStore(
        [
          { annotations: [ann("a1", "dog_face")] },
          { annotations: [ann("a2", "dog_face", { embeddingCounts: {} })] },
        ],
        [FACE, HOTSPOTTER],
      );
      const store = new NewMatchStore(encounterStore);
      expect(store.algorithms).toEqual([FACE.description]);

      runInAction(() => {
        encounterStore.selectedImageIndex = 1;
      });

      expect(labels(store)).toEqual([HOTSPOTTER.description]);
      expect(store.algorithms).toEqual([]);
      expect(store.matchingAlgorithms).toEqual([]);
    });

    test("works when encounter data arrives before site settings", () => {
      const encounterStore = new EncounterStore();
      const store = encounterStore.newMatch;

      encounterStore.setEncounterData({
        id: "E-1",
        taxonomy: "Canis familiaris",
        mediaAssets: [{ annotations: [ann("a1", "dog+body")] }],
      });
      expect(store.algorithms).toEqual([]);

      encounterStore.setSiteSettings({
        iaConfig: { "Canis familiaris": [FACE, BODY, HOTSPOTTER] },
      });

      expect(labels(store)).toEqual([BODY.description]);
      expect(store.algorithms).toEqual([BODY.description]);
    });

    test("the posted payload carries only the narrowed matchers", async () => {
      axios.post.mockResolvedValue({ status: 200, data: { taskId: "t" } });
      const store = new NewMatchStore(
        makeDogStore([{ annotations: [ann("a1", "dog_face")] }]),
      );

      await store.buildNewMatchPayload();

      const payload = axios.post.mock.calls[0][1];
      expect(payload.taskParameters.matchingAlgorithms).toEqual([FACE]);
      expect(payload.annotationIds).toEqual(["a1"]);
    });

    test("works through a real EncounterStore, with data arriving after construction", () => {
      const encounterStore = new EncounterStore();
      const store = encounterStore.newMatch;
      expect(store.algorithmOptions).toEqual([]);

      encounterStore.setSiteSettings({
        iaConfig: { "Canis familiaris": [FACE, BODY, HOTSPOTTER] },
      });
      encounterStore.setEncounterData({
        id: "E-1",
        taxonomy: "Canis familiaris",
        locationId: "LOC-1",
        mediaAssets: [
          { annotations: [ann("a1", "dog_face")] },
          { annotations: [ann("a2", "dog+body")] },
        ],
      });

      expect(store.locationId).toEqual(["LOC-1"]);
      expect(store.algorithms).toEqual([FACE.description]);

      encounterStore.setSelectedImageIndex(1);

      expect(labels(store)).toEqual([BODY.description]);
      expect(store.algorithms).toEqual([BODY.description]);
    });
  });
});
