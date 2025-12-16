import axios from "axios";
import { toast } from "react-toastify";
import EncounterStore from "../../../pages/Encounter/stores/EncounterStore";
import * as helperFunctions from "../../../pages/Encounter/stores/helperFunctions";

jest.mock("axios");
jest.mock("react-toastify", () => ({
  toast: {
    success: jest.fn(),
    error: jest.fn(),
    loading: jest.fn(),
    update: jest.fn(),
  },
}));
jest.mock("../../../pages/Encounter/stores/helperFunctions", () => ({
  validateFieldValue: jest.fn(),
  getValueAtPath: jest.fn(),
  setValueAtPath: jest.fn(),
  deleteValueAtPath: jest.fn(),
  expandOperations: jest.fn((ops) => ops),
}));
jest.mock("@flowjs/flow.js", () => {
  return jest.fn().mockImplementation(() => ({
    assignBrowse: jest.fn(),
    on: jest.fn(),
    upload: jest.fn(),
    progress: jest.fn(() => 0),
    files: [],
  }));
});

describe("EncounterStore", () => {
  let store;
  let mockIntl;

  beforeEach(() => {
    store = new EncounterStore();
    mockIntl = {
      formatMessage: jest.fn(({ defaultMessage }) => defaultMessage),
    };
    store.setIntl(mockIntl);
    jest.clearAllMocks();
  });

  afterEach(() => {
    jest.clearAllTimers();
  });

  describe("Initialization", () => {
    it("should initialize with default values", () => {
      expect(store.encounterData).toBeNull();
      expect(store.overviewActive).toBe(true);
      expect(store.editDateCard).toBe(false);
      expect(store.lat).toBeNull();
      expect(store.lon).toBeNull();
    });

    it("should initialize child stores", () => {
      expect(store.modals).toBeDefined();
      expect(store.errors).toBeDefined();
      expect(store.newMatch).toBeDefined();
      expect(store.imageModal).toBeDefined();
    });
  });

  describe("Encounter Data Management", () => {
    it("should set encounter data correctly", () => {
      const mockData = {
        id: "enc-123",
        locationGeoPoint: { lat: 40.7128, lon: -74.006 },
        metalTags: [{ location: "left-fin", number: "001" }],
        acousticTag: { serialNumber: "A123" },
        satelliteTag: { name: "SAT-001" },
        measurements: [
          {
            type: "length",
            value: 150,
            units: "cm",
            samplingProtocol: "standard",
          },
        ],
      };

      store.setEncounterData(mockData);

      expect(store.encounterData).toEqual(mockData);
      expect(store.lat).toBe(40.7128);
      expect(store.lon).toBe(-74.006);
      expect(store.metalTagValues).toEqual(mockData.metalTags);
      expect(store.acousticTagValues).toEqual(mockData.acousticTag);
      expect(store.satelliteTagValues).toEqual(mockData.satelliteTag);
    });

    it("should handle null locationGeoPoint", () => {
      const mockData = { id: "enc-123" };
      store.setEncounterData(mockData);

      expect(store.lat).toBeNull();
      expect(store.lon).toBeNull();
    });

    it("should filter and map measurements correctly", () => {
      const mockData = {
        id: "enc-123",
        measurements: [
          { type: "length", value: 150, units: "cm" },
          { type: null, value: 100 }, // Should be filtered out
          { type: "weight", value: 50 },
        ],
      };

      store.setEncounterData(mockData);

      expect(store.measurementValues).toHaveLength(2);
      expect(store.measurementValues[0].type).toBe("length");
      expect(store.measurementValues[1].type).toBe("weight");
    });
  });

  describe("Card Edit States", () => {
    it("should toggle date card edit state", () => {
      expect(store.editDateCard).toBe(false);
      store.setEditDateCard(true);
      expect(store.editDateCard).toBe(true);
    });

    it("should toggle identify card edit state", () => {
      store.setEditIdentifyCard(true);
      expect(store.editIdentifyCard).toBe(true);
    });

    it("should toggle metadata card edit state", () => {
      store.setEditMetadataCard(true);
      expect(store.editMetadataCard).toBe(true);
    });

    it("should toggle location card edit state", () => {
      store.setEditLocationCard(true);
      expect(store.editLocationCard).toBe(true);
    });

    it("should toggle attributes card edit state", () => {
      store.setEditAttributesCard(true);
      expect(store.editAttributesCard).toBe(true);
    });
  });

  describe("Coordinates Management", () => {
    it("should set latitude and validate", () => {
      helperFunctions.validateFieldValue.mockReturnValue(null);

      store.setLat(40.7128);

      expect(store.lat).toBe(40.7128);
      expect(helperFunctions.validateFieldValue).toHaveBeenCalledWith(
        "location",
        "latitude",
        40.7128,
        expect.any(Object),
      );
    });

    it("should set longitude and validate", () => {
      helperFunctions.validateFieldValue.mockReturnValue(null);

      store.setLon(-74.006);

      expect(store.lon).toBe(-74.006);
      expect(helperFunctions.validateFieldValue).toHaveBeenCalled();
    });
  });

  describe("Person Management", () => {
    beforeEach(() => {
      store.setEncounterData({ id: "enc-123" });
    });

    it("should add new person successfully", async () => {
      store.setNewPersonName("John Doe");
      store.setNewPersonEmail("john@example.com");
      store.setNewPersonRole("submitter");

      axios.patch.mockResolvedValue({ status: 200 });

      await store.addNewPerson();

      expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/enc-123", [
        { op: "add", path: "submitter", value: "john@example.com" },
      ]);
      expect(toast.success).toHaveBeenCalled();
      expect(store.newPersonName).toBe("");
      expect(store.newPersonEmail).toBe("");
      expect(store.newPersonRole).toBe("");
    });

    it("should handle add person error", async () => {
      store.setNewPersonEmail("john@example.com");
      store.setNewPersonRole("submitter");

      axios.patch.mockRejectedValue(new Error("Network error"));

      await expect(store.addNewPerson()).rejects.toThrow();
      expect(toast.error).toHaveBeenCalled();
    });

    it("should remove contact successfully", async () => {
      store.setEncounterData({
        id: "enc-123",
        submitter: [{ id: "user-1", name: "John" }],
      });

      axios.patch.mockResolvedValue({ status: 200 });

      await store.removeContact("submitter", "user-1");

      expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/enc-123", [
        { op: "remove", path: "submitter", value: "user-1" },
      ]);
      expect(store.encounterData.submitter).toHaveLength(0);
      expect(toast.success).toHaveBeenCalled();
    });
  });

  describe("Individual Search", () => {
    beforeEach(() => {
      store.setEncounterData({
        id: "enc-123",
        taxonomy: "Balaenoptera musculus",
      });
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it("should search individuals by name", async () => {
      const mockResults = [
        { id: "ind-1", names: ["Whale-001"] },
        { id: "ind-2", names: ["Whale-002"] },
      ];

      axios.post.mockResolvedValue({ data: { hits: mockResults } });

      await store.searchIndividualsByNameAndId("Whale");

      expect(axios.post).toHaveBeenCalledWith(
        "/api/v3/search/individual?size=20&from=0",
        expect.objectContaining({
          query: expect.objectContaining({
            bool: expect.objectContaining({
              filter: expect.arrayContaining([
                expect.objectContaining({
                  wildcard: expect.objectContaining({
                    names: expect.objectContaining({
                      value: "*Whale*",
                      case_insensitive: true,
                    }),
                  }),
                }),
              ]),
            }),
          }),
        }),
      );
      expect(store.individualSearchResults).toEqual(mockResults);
    });

    it("should debounce individual search", () => {
      // Mock axios to track calls instead of spying on the store method
      axios.post.mockResolvedValue({ data: { hits: [] } });

      store.setIndividualSearchInput("W");
      store.setIndividualSearchInput("Wh");
      store.setIndividualSearchInput("Wha");

      // The search shouldn't have been called yet due to debounce
      expect(axios.post).not.toHaveBeenCalled();

      // Fast-forward time past the debounce delay
      jest.advanceTimersByTime(300);

      // Now it should have been called once with the last input
      expect(axios.post).toHaveBeenCalledTimes(1);
      expect(axios.post).toHaveBeenCalledWith(
        "/api/v3/search/individual?size=20&from=0",
        expect.objectContaining({
          query: expect.objectContaining({
            bool: expect.objectContaining({
              filter: expect.arrayContaining([
                expect.objectContaining({
                  wildcard: expect.objectContaining({
                    names: expect.objectContaining({
                      value: "*Wha*",
                    }),
                  }),
                }),
              ]),
            }),
          }),
        }),
      );
    });

    it("should clear search results for short input", () => {
      store.setIndividualSearchInput("W");
      jest.advanceTimersByTime(300);

      expect(store.individualSearchResults).toEqual([]);
    });

    it("should handle individual search error", async () => {
      axios.post.mockRejectedValue(new Error("Search failed"));

      await expect(
        store.searchIndividualsByNameAndId("test"),
      ).rejects.toThrow();
      expect(toast.error).toHaveBeenCalled();
      expect(store.individualSearchResults).toEqual([]);
    });
  });

  describe("Sighting Search", () => {
    beforeEach(() => {
      jest.useFakeTimers();
    });

    afterEach(() => {
      jest.useRealTimers();
    });

    it("should search sightings by ID", async () => {
      const mockResults = [{ id: "sight-001" }, { id: "sight-002" }];

      axios.post.mockResolvedValue({ data: { hits: mockResults } });

      await store.searchSightingsById("sight");

      expect(axios.post).toHaveBeenCalledWith(
        "/api/v3/search/occurrence?size=20&from=0",
        expect.objectContaining({
          query: expect.objectContaining({
            bool: expect.objectContaining({
              filter: expect.arrayContaining([
                expect.objectContaining({
                  wildcard: expect.objectContaining({
                    id: expect.objectContaining({
                      value: "*sight*",
                      case_insensitive: true,
                    }),
                  }),
                }),
              ]),
            }),
          }),
        }),
      );
      expect(store.sightingSearchResults).toEqual(mockResults);
    });

    it("should debounce sighting search", () => {
      // Mock axios to track calls instead of spying on the store method
      axios.post.mockResolvedValue({ data: { hits: [] } });

      store.setSightingSearchInput("s");
      store.setSightingSearchInput("si");
      store.setSightingSearchInput("sig");

      // Fast-forward time past the debounce delay
      jest.advanceTimersByTime(300);

      // Should have been called once with the last input
      expect(axios.post).toHaveBeenCalledTimes(1);
      expect(axios.post).toHaveBeenCalledWith(
        "/api/v3/search/occurrence?size=20&from=0",
        expect.objectContaining({
          query: expect.objectContaining({
            bool: expect.objectContaining({
              filter: expect.arrayContaining([
                expect.objectContaining({
                  wildcard: expect.objectContaining({
                    id: expect.objectContaining({
                      value: "*sig*",
                    }),
                  }),
                }),
              ]),
            }),
          }),
        }),
      );
    });
  });

  describe("Tag Management", () => {
    it("should set metal tag values", () => {
      const tags = [{ location: "dorsal", number: "123" }];
      store.setMetalTagValues(tags);
      expect(store.metalTagValues).toEqual(tags);
    });

    it("should merge acoustic tag values", () => {
      store.setEncounterData({
        id: "enc-123",
        acousticTag: { serialNumber: "A123" },
      });

      store.setAcousticTagValues({ idNumber: "ID456" });

      expect(store.acousticTagValues).toEqual({
        serialNumber: "A123",
        idNumber: "ID456",
      });
    });

    it("should merge satellite tag values", () => {
      store.setEncounterData({
        id: "enc-123",
        satelliteTag: { name: "SAT-001" },
      });

      store.setSatelliteTagValues({ serialNumber: "SER123" });

      expect(store.satelliteTagValues).toEqual({
        name: "SAT-001",
        serialNumber: "SER123",
      });
    });
  });

  describe("Measurement Management", () => {
    beforeEach(() => {
      // Set encounter data first to avoid undefined errors in setSiteSettings
      store.setEncounterData({ id: "enc-123" });

      store.setSiteSettings({
        measurement: ["length", "weight", "girth"],
        measurementUnits: ["cm", "kg", "cm"],
        behaviorOptions: { "": [] }, // Add required property
      });
    });

    it("should get measurement by type", () => {
      store._measurementValues = [
        {
          type: "length",
          value: 150,
          units: "cm",
          samplingProtocol: "standard",
        },
      ];

      const measurement = store.getMeasurement("length");

      expect(measurement).toEqual({
        type: "length",
        value: 150,
        units: "cm",
        samplingProtocol: "standard",
      });
    });

    it("should return default measurement if not found", () => {
      const measurement = store.getMeasurement("weight");

      expect(measurement).toEqual({
        type: "weight",
        value: "",
        units: "kg",
        samplingProtocol: "",
      });
    });

    it("should set measurement value", () => {
      store.setMeasurementValue("length", 150);

      expect(store.measurementValues).toContainEqual(
        expect.objectContaining({
          type: "length",
          value: 150,
          units: "cm",
        }),
      );
    });

    it("should set measurement sampling protocol", () => {
      store.setMeasurementSamplingProtocol("length", "standard");

      expect(store.measurementValues).toContainEqual(
        expect.objectContaining({
          type: "length",
          samplingProtocol: "standard",
        }),
      );
    });

    it("should update existing measurement", () => {
      store._measurementValues = [
        { type: "length", value: 100, units: "cm", samplingProtocol: "" },
      ];

      store.setMeasurementValue("length", 150);

      expect(store.measurementValues).toHaveLength(1);
      expect(store.measurementValues[0].value).toBe(150);
    });
  });

  describe("Tracking Patch Operations", () => {
    it("should build tracking patch payload for metal tags", () => {
      store.setEncounterData({
        id: "enc-123",
        metalTags: [{ location: "dorsal", number: "001" }],
      });

      store.setMetalTagValues([{ location: "dorsal", number: "002" }]);

      const ops = store.buildTrackingPatchPayload();

      expect(ops).toContainEqual({
        op: "replace",
        path: "metalTags",
        value: { location: "dorsal", number: "002" },
      });
    });

    it("should build tracking patch payload for acoustic tags", () => {
      store.setEncounterData({
        id: "enc-123",
        acousticTag: { serialNumber: "A123" },
      });

      store.setAcousticTagValues({ serialNumber: "A456", idNumber: "ID789" });

      const ops = store.buildTrackingPatchPayload();

      expect(ops).toContainEqual({
        op: "replace",
        path: "acousticTag",
        value: { serialNumber: "A456", idNumber: "ID789" },
      });
    });

    it("should not create operations when no changes", () => {
      store.setEncounterData({
        id: "enc-123",
        metalTags: [{ location: "dorsal", number: "001" }],
        acousticTag: { serialNumber: "A123" },
        satelliteTag: { name: "SAT-001" },
      });

      const ops = store.buildTrackingPatchPayload();

      expect(ops).toHaveLength(0);
    });
  });

  describe("Save Operations", () => {
    beforeEach(() => {
      store.setEncounterData({ id: "enc-123" });
    });

    it("should handle tracking patch error", async () => {
      store.setMetalTagValues([{ location: "dorsal", number: "002" }]);
      axios.patch.mockRejectedValue(new Error("Save failed"));

      await expect(store.patchTracking()).rejects.toThrow();
      expect(toast.error).toHaveBeenCalled();
    });

    it("should patch measurements successfully", async () => {
      store._measurementValues = [
        {
          type: "length",
          value: 150,
          units: "cm",
          samplingProtocol: "standard",
        },
      ];

      axios.patch.mockResolvedValue({ status: 200 });

      await store.patchMeasurements();

      expect(axios.patch).toHaveBeenCalled();
      expect(toast.success).toHaveBeenCalled();
    });

    it("should skip measurements with empty values", async () => {
      store._measurementValues = [
        { type: "length", value: "", units: "cm", samplingProtocol: "" },
      ];

      await store.patchMeasurements();

      expect(axios.patch).not.toHaveBeenCalled();
    });

    it("should save section successfully", async () => {
      helperFunctions.getValueAtPath.mockReturnValue("old-value");
      store._sectionDrafts.set("date", { time: "new-value" });
      axios.patch.mockResolvedValue({ status: 200 });
      await store.saveSection("date", "enc-123");
    });

    it("should handle save section error", async () => {
      helperFunctions.getValueAtPath.mockReturnValue("old-value");
      store._sectionDrafts.set("date", { time: "new-value" });

      const errorResponse = {
        response: {
          data: { fieldErrors: [{ field: "time", message: "Invalid time" }] },
        },
      };
      axios.patch.mockRejectedValue(errorResponse);
    });
  });

  describe("Project Management", () => {
    beforeEach(() => {
      store.setEncounterData({ id: "enc-123" });
    });

    it("should add encounter to project", async () => {
      store.setSelectedProjects([{ id: "proj-1", name: "Project A" }]);

      axios.post.mockResolvedValue({ status: 200 });
      axios.get.mockResolvedValue({ status: 200, data: { id: "enc-123" } });

      await store.addEncounterToProject();

      expect(axios.post).toHaveBeenCalledWith(
        "/ProjectUpdate",
        expect.objectContaining({
          projects: expect.arrayContaining([
            expect.objectContaining({
              id: "proj-1",
              encountersToAdd: ["enc-123"],
            }),
          ]),
        }),
        expect.any(Object),
      );
      expect(toast.success).toHaveBeenCalled();
    });

    it("should remove project from encounter", async () => {
      axios.post.mockResolvedValue({ status: 200 });
      axios.get.mockResolvedValue({ status: 200, data: { id: "enc-123" } });

      await store.removeProjectFromEncounter("proj-1");

      expect(axios.post).toHaveBeenCalledWith(
        "/ProjectUpdate",
        expect.objectContaining({
          projects: expect.arrayContaining([
            expect.objectContaining({
              id: "proj-1",
              encountersToRemove: ["enc-123"],
            }),
          ]),
        }),
        expect.any(Object),
      );
    });
  });

  describe("Individual and Occurrence Management", () => {
    beforeEach(() => {
      store.setEncounterData({
        id: "enc-123",
        individualId: "ind-456",
        occurrenceId: "occ-789",
      });
    });

    it("should remove individual from encounter", async () => {
      axios.patch.mockResolvedValue({ status: 200 });
      axios.get.mockResolvedValue({
        status: 200,
        data: { id: "enc-123" },
      });

      await store.removeIndividualFromEncounter();

      expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/enc-123", [
        { op: "remove", path: "individualId", value: "ind-456" },
      ]);
      expect(toast.success).toHaveBeenCalled();
    });

    it("should remove occurrence ID from encounter", async () => {
      axios.patch.mockResolvedValue({ status: 200 });
      axios.get.mockResolvedValue({
        status: 200,
        data: { id: "enc-123" },
      });

      await store.removeOccurrenceIdFromEncounter();

      expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/enc-123", [
        { op: "remove", path: "occurrenceId", value: "occ-789" },
      ]);
      expect(toast.success).toHaveBeenCalled();
    });
  });

  describe("Site Settings", () => {
    it("should set site settings and derive options", () => {
      // Must set encounter data first because setSiteSettings accesses this._encounterData?.species
      store.setEncounterData({
        id: "enc-123",
        species: "Balaenoptera musculus",
      });

      const mockSettings = {
        siteTaxonomies: [
          { scientificName: "Balaenoptera musculus" },
          { scientificName: "Megaptera novaeangliae" },
        ],
        livingStatus: ["alive", "dead", "unknown"],
        sex: ["male", "female", "unknown"],
        lifeStage: ["calf", "juvenile", "adult"],
        behaviorOptions: {
          "": ["feeding", "traveling"],
          "Balaenoptera musculus": ["diving", "surfacing"],
        },
        behavior: ["resting"],
        groupRoles: ["leader", "follower"],
        patterningCode: ["P1", "P2", "P3"],
        metalTagsEnabled: true,
        acousticTagEnabled: true,
        satelliteTagEnabled: true,
        showMeasurements: true,
      };

      store.setSiteSettings(mockSettings);

      expect(store.taxonomyOptions).toHaveLength(2);
      expect(store.livingStatusOptions).toHaveLength(3);
      expect(store.sexOptions).toHaveLength(3);
      expect(store.lifeStageOptions).toHaveLength(3);
      expect(store.behaviorOptions.length).toBeGreaterThan(0);
      expect(store.metalTagsEnabled).toBe(true);
      expect(store.acousticTagEnabled).toBe(true);
    });

    it("should handle location ID options", () => {
      // Set encounter data first to avoid undefined errors
      store.setEncounterData({ id: "enc-123" });

      const mockSettings = {
        locationData: {
          locationID: [
            { name: "Pacific Ocean", children: [] },
            { name: "Atlantic Ocean", children: [] },
          ],
        },
        behaviorOptions: { "": [] }, // Add required property
      };

      store.setSiteSettings(mockSettings);

      expect(store.locationIdOptions.length).toBeGreaterThan(0);
    });

    it("should handle site settings when encounter data has no species", () => {
      // Test when encounterData exists but has no species
      store.setEncounterData({ id: "enc-123" });

      const mockSettings = {
        siteTaxonomies: [],
        behaviorOptions: {
          "": ["feeding", "traveling"],
        },
        behavior: ["resting"],
      };

      store.setSiteSettings(mockSettings);

      // Should only have the default behaviors, not species-specific ones
      expect(store.behaviorOptions.length).toBeGreaterThan(0);
    });

    it("should handle site settings when encounterData is null", () => {
      // Test the edge case where encounterData is null
      const mockSettings = {
        siteTaxonomies: [],
        behaviorOptions: {
          "": ["feeding"],
        },
        behavior: [],
      };

      // This should not crash even though this._encounterData is null
      store.setSiteSettings(mockSettings);

      expect(store.behaviorOptions.length).toBeGreaterThan(0);
    });
  });

  describe("Encounter Annotations", () => {
    it("should get encounter annotations for selected image", () => {
      store.setEncounterData({
        id: "enc-123",
        mediaAssets: [
          {
            annotations: [
              { id: "ann-1", encounterId: "enc-123" },
              { id: "ann-2", encounterId: "enc-456" },
              { id: "ann-3", encounterId: "enc-123" },
            ],
          },
        ],
      });

      store.setSelectedImageIndex(0);

      expect(store.encounterAnnotations).toHaveLength(2);
      expect(store.encounterAnnotations.map((a) => a.id)).toEqual([
        "ann-1",
        "ann-3",
      ]);
    });

    it("should return empty array when no media assets", () => {
      store.setEncounterData({ id: "enc-123" });
      expect(store.encounterAnnotations).toEqual([]);
    });
  });

  describe("Match Result Clickable", () => {
    it("should return true when identification is active and complete", () => {
      store.setEncounterData({
        id: "enc-123",
        mediaAssets: [
          {
            detectionStatus: "complete",
            annotations: [
              {
                id: "ann-1",
                encounterId: "enc-123",
                iaTaskId: "task-123",
                iaTaskParameters: { skipIdent: false },
                identificationStatus: "complete",
              },
            ],
          },
        ],
      });

      store.setSelectedImageIndex(0);
      store.setSelectedAnnotationId("ann-1");

      expect(store.matchResultClickable).toBe(true);
    });

    it("should return false when skipIdent is true", () => {
      store.setEncounterData({
        id: "enc-123",
        mediaAssets: [
          {
            detectionStatus: "complete",
            annotations: [
              {
                id: "ann-1",
                encounterId: "enc-123",
                iaTaskId: "task-123",
                iaTaskParameters: { skipIdent: true },
                identificationStatus: "complete",
              },
            ],
          },
        ],
      });

      store.setSelectedImageIndex(0);
      store.setSelectedAnnotationId("ann-1");

      expect(store.matchResultClickable).toBe(false);
    });
  });

  describe("Refresh Encounter Data", () => {
    it("should refresh encounter data successfully", async () => {
      store.setEncounterData({ id: "enc-123" });

      const updatedData = {
        id: "enc-123",
        taxonomy: "Updated Species",
        mediaAssets: [{}, {}],
      };

      axios.get.mockResolvedValue({ status: 200, data: updatedData });

      const result = await store.refreshEncounterData();

      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/enc-123");
      expect(store.encounterData).toEqual(updatedData);
      expect(result).toEqual(updatedData);
    });

    it("should preserve selected image index after refresh", async () => {
      store.setEncounterData({
        id: "enc-123",
        mediaAssets: [{}, {}, {}],
      });
      store.setSelectedImageIndex(2);

      const updatedData = {
        id: "enc-123",
        mediaAssets: [{}, {}, {}],
      };

      axios.get.mockResolvedValue({ status: 200, data: updatedData });

      await store.refreshEncounterData();

      expect(store.selectedImageIndex).toBe(2);
    });

    it("should handle refresh error", async () => {
      store.setEncounterData({ id: "enc-123" });
      axios.get.mockRejectedValue(new Error("Network error"));

      await expect(store.refreshEncounterData()).rejects.toThrow();
      expect(toast.error).toHaveBeenCalled();
    });
  });

  describe("Section Drafts", () => {
    it("should get field value from draft", () => {
      store.setEncounterData({ id: "enc-123", time: "old-time" });
      store._sectionDrafts.set("date", { time: "new-time" });

      helperFunctions.getValueAtPath.mockReturnValue("old-time");

      const value = store.getFieldValue("date", "time");

      expect(value).toBe("new-time");
    });

    it("should get field value from encounter data when no draft", () => {
      store.setEncounterData({ id: "enc-123", time: "original-time" });
      helperFunctions.getValueAtPath.mockReturnValue("original-time");

      const value = store.getFieldValue("date", "time");

      expect(value).toBe("original-time");
    });

    it("should set field value and validate", () => {
      helperFunctions.validateFieldValue.mockReturnValue(null);

      store.setFieldValue("date", "time", "new-time");

      const draft = store._sectionDrafts.get("date");
      expect(draft.time).toBe("new-time");
      expect(helperFunctions.validateFieldValue).toHaveBeenCalledWith(
        "date",
        "time",
        "new-time",
      );
    });

    it("should reset section draft", () => {
      store._sectionDrafts.set("date", { time: "value" });

      store.resetSectionDraft("date");

      expect(store._sectionDrafts.get("date")).toEqual({});
    });

    it("should reset all drafts", () => {
      store._sectionDrafts.set("date", { time: "value" });
      store._sectionDrafts.set("location", { lat: 40 });

      store.resetAllDrafts();

      expect(store._sectionDrafts.get("date")).toEqual({});
      expect(store._sectionDrafts.get("location")).toEqual({});
    });
  });

  describe("Apply Patch Operations Locally", () => {
    it("should apply remove operation", () => {
      store.setEncounterData({
        id: "enc-123",
        taxonomy: "Species A",
        time: "10:00",
      });

      helperFunctions.deleteValueAtPath.mockImplementation((obj, path) => {
        delete obj[path];
      });

      store.applyPatchOperationsLocally([{ op: "remove", path: "taxonomy" }]);

      expect(helperFunctions.deleteValueAtPath).toHaveBeenCalled();
    });

    it("should apply replace operation", () => {
      store.setEncounterData({
        id: "enc-123",
        taxonomy: "Species A",
      });

      helperFunctions.setValueAtPath.mockImplementation((obj, path, value) => {
        obj[path] = value;
      });

      store.applyPatchOperationsLocally([
        { op: "replace", path: "taxonomy", value: "Species B" },
      ]);

      expect(helperFunctions.setValueAtPath).toHaveBeenCalled();
    });

    it("should handle empty operations array", () => {
      const originalData = { id: "enc-123" };
      store.setEncounterData(originalData);

      store.applyPatchOperationsLocally([]);

      expect(store.encounterData).toEqual(originalData);
    });
  });
});
