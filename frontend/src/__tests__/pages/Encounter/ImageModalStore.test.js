import ImageModalStore from "../../../pages/Encounter/stores/ImageModalStore";
import axios from "axios";
import { toast } from "react-toastify";

jest.mock("axios");
jest.mock("react-toastify", () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
  },
}));

const makeEncounterStore = (overrides = {}) => ({
  selectedImageIndex: 0,
  setSelectedImageIndex: jest.fn(),
  encounterAnnotations: [{ id: "ann-1" }],
  selectedAnnotationId: null,
  setSelectedAnnotationId: jest.fn(),
  currentAnnotation: { id: "ann-1" },
  matchResultClickable: true,
  modals: {
    setOpenMatchCriteriaModal: jest.fn(),
  },
  siteSettingsData: {
    keyword: ["kw1", "kw2"],
    keywordId: ["k1", "k2"],
    labeledKeyword: {
      behavior: {},
      color: {},
    },
    labeledKeywordAllowedValues: {
      behavior: ["sleeping", "feeding"],
    },
  },
  encounterData: {
    id: "E-1",
    mediaAssets: [
      {
        id: "ma-1",
        keywords: [{ id: "k1", displayName: "kw1" }],
      },
      {
        id: "ma-2",
        keywords: [],
      },
    ],
  },
  refreshEncounterData: jest.fn(),
  ...overrides,
});

describe("ImageModalStore", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("selectedImageIndex delegates to encounterStore", () => {
    const encounterStore = makeEncounterStore({ selectedImageIndex: 1 });
    const store = new ImageModalStore(encounterStore);

    expect(store.selectedImageIndex).toBe(1);

    store.setSelectedImageIndex(2);
    expect(encounterStore.setSelectedImageIndex).toHaveBeenCalledWith(2);
  });

  test("selectedAnnotationId delegates to encounterStore", () => {
    const encounterStore = makeEncounterStore({
      selectedAnnotationId: "ann-1",
    });
    const store = new ImageModalStore(encounterStore);

    expect(store.selectedAnnotationId).toBe("ann-1");

    store.setSelectedAnnotationId("ann-2");
    expect(encounterStore.setSelectedAnnotationId).toHaveBeenCalledWith(
      "ann-2",
    );
  });

  test("tags returns keywords of current media asset", () => {
    const encounterStore = makeEncounterStore({
      selectedImageIndex: 0,
    });
    const store = new ImageModalStore(encounterStore);

    const tags = store.tags;
    expect(tags).toEqual([{ id: "k1", displayName: "kw1" }]);
  });

  test("availableKeywords and availableKeywordsId derived from siteSettings", () => {
    const encounterStore = makeEncounterStore();
    const store = new ImageModalStore(encounterStore);

    expect(store.availableKeywords).toEqual(["kw1", "kw2"]);
    expect(store.availableKeywordsId).toEqual(["k1", "k2"]);
  });

  test("labeledKeywordAllowedValues returns values from selected labeled keyword", () => {
    const encounterStore = makeEncounterStore();
    const store = new ImageModalStore(encounterStore);

    expect(store.labeledKeywordAllowedValues).toEqual([]);

    store.setSelectedLabeledKeyword("behavior");
    expect(store.labeledKeywordAllowedValues).toEqual(["sleeping", "feeding"]);
  });

  test("setOpenMatchCriteriaModal calls encounterStore.modal", () => {
    const encounterStore = makeEncounterStore();
    const store = new ImageModalStore(encounterStore);

    store.setOpenMatchCriteriaModal(true);
    expect(
      encounterStore.modals.setOpenMatchCriteriaModal,
    ).toHaveBeenCalledWith(true);
  });

  test("removeAnnotation success path", async () => {
    const encounterStore = makeEncounterStore({
      encounterData: {
        id: "E-1",
        mediaAssets: [],
      },
      setSelectedAnnotationId: jest.fn(),
    });
    const store = new ImageModalStore(encounterStore);

    axios.patch.mockResolvedValueOnce({ status: 200 });

    await store.removeAnnotation("ann-9");

    expect(axios.patch).toHaveBeenCalledWith(
      "/api/v3/encounters/E-1",
      [
        {
          op: "remove",
          path: "annotations",
          value: "ann-9",
        },
      ],
      {
        headers: { "Content-Type": "application/json" },
      },
    );
    expect(encounterStore.setSelectedAnnotationId).toHaveBeenCalledWith(null);
    expect(toast.success).toHaveBeenCalledWith(
      "Annotation removed successfully",
    );
  });

  test("removeAnnotation error path", async () => {
    const encounterStore = makeEncounterStore({
      encounterData: {
        id: "E-1",
        mediaAssets: [],
      },
    });
    const store = new ImageModalStore(encounterStore);

    axios.patch.mockRejectedValueOnce(new Error("network"));

    await expect(store.removeAnnotation("ann-1")).rejects.toThrow("network");
    expect(toast.error).toHaveBeenCalledWith("Failed to remove annotation");
  });

  test("deleteImage throws when no current media asset", async () => {
    const encounterStore = makeEncounterStore({
      encounterData: {
        id: "E-1",
        mediaAssets: [],
      },
    });
    const store = new ImageModalStore(encounterStore);

    await expect(store.deleteImage()).rejects.toThrow(
      "No media asset selected",
    );
  });

  test("deleteImage success path", async () => {
    const encounterStore = makeEncounterStore({
      selectedImageIndex: 0,
      encounterData: {
        id: "E-1",
        mediaAssets: [
          {
            id: "ma-1",
          },
        ],
      },
    });
    const store = new ImageModalStore(encounterStore);

    axios.post.mockResolvedValueOnce({ status: 200 });

    await store.deleteImage();

    expect(axios.post).toHaveBeenCalledWith(
      "/MediaAssetAttach",
      {
        detach: "true",
        EncounterID: "E-1",
        MediaAssetID: "ma-1",
      },
      {
        headers: { "Content-Type": "application/json" },
      },
    );

    expect(toast.success).toHaveBeenCalledWith("Image deleted successfully");
    expect(encounterStore.refreshEncounterData).toHaveBeenCalled();
  });

  test("reset clears ui-only state and deselects annotation", () => {
    const encounterStore = makeEncounterStore();
    const store = new ImageModalStore(encounterStore);

    store._addTagsFieldOpen = true;
    store._selectedKeyword = "k1";
    store._selectedLabeledKeyword = "behavior";
    store._selectedAllowedValues = "sleeping";

    store.reset();

    expect(encounterStore.setSelectedAnnotationId).toHaveBeenCalledWith(null);
    expect(store.addTagsFieldOpen).toBe(false);
    expect(store.selectedKeyword).toBe(null);
    expect(store.selectedLabeledKeyword).toBe(null);
    expect(store.selectedAllowedValues).toBe(null);
  });

  test("showAnnotations getter/setter", () => {
    const encounterStore = makeEncounterStore();
    const store = new ImageModalStore(encounterStore);

    expect(store.showAnnotations).toBe(true);
    store.setShowAnnotations(false);
    expect(store.showAnnotations).toBe(false);
  });
});
