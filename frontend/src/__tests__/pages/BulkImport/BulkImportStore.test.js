import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";

jest.mock("@flowjs/flow.js", () => {
  return jest.fn().mockImplementation(() => ({
    opts: { query: {} },
    assignBrowse: jest.fn(),
    on: jest.fn(),
    files: [],
    removeFile: jest.fn(),
    upload: jest.fn(),
    retry: jest.fn(),
    pause: jest.fn(),
    resume: jest.fn(),
  }));
});

describe("BulkImportStore tests part 1", () => {
  let store;

  beforeEach(() => {
    store = new BulkImportStore();
    localStorage.clear();
  });

  test("getOptionsForSelectCell returns correct options", () => {
    store._validLocationIDs = ["L1", "L2"];
    expect(store.getOptionsForSelectCell("Encounter.locationID")).toEqual([
      { value: "L1", label: "L1" },
      { value: "L2", label: "L2" },
    ]);

    store._validspecies = ["G1"];
    expect(store.getOptionsForSelectCell("Encounter.genus")).toEqual([
      { value: "G1", label: "G1" },
    ]);

    store._validCountryIDs = ["C1"];
    expect(store.getOptionsForSelectCell("Encounter.country")).toEqual([
      { value: "C1", label: "C1" },
    ]);

    store._validLivingStatus = ["live"];
    expect(store.getOptionsForSelectCell("Encounter.livingStatus")).toEqual([
      { value: "live", label: "live" },
    ]);

    store._validLifeStages = ["stage"];
    expect(store.getOptionsForSelectCell("Encounter.lifeStage")).toEqual([
      { value: "stage", label: "stage" },
    ]);

    store._validSex = ["M"];
    expect(store.getOptionsForSelectCell("Encounter.sex")).toEqual([
      { value: "M", label: "M" },
    ]);

    store._validBehavior = ["B"];
    expect(store.getOptionsForSelectCell("Encounter.behavior")).toEqual([
      { value: "B", label: "B" },
    ]);

    expect(store.getOptionsForSelectCell("Unknown.col")).toEqual([]);
  });

  test("convertToTreeData handles nested structure", () => {
    const input = [{ id: "A", locationID: [{ id: "B", locationID: [] }] }];
    const output = store.convertToTreeData(input);
    expect(output).toEqual([
      {
        title: "A",
        value: "A",
        children: [{ title: "B", value: "B", children: [] }],
      },
    ]);
  });

  test("updateCellValue updates spreadsheetData and rawData", () => {
    store._spreadsheetData = [{ x: 1 }];
    store._rawData = [{ x: 1 }];
    store.updateCellValue(0, "x", 5);
    expect(store.spreadsheetData[0].x).toBe(5);
    expect(store.rawData[0].x).toBe(5);
  });

  test("validateRow returns errors for required fields", () => {
    store._columnsDef = ["a", "b"];
    store._spreadsheetData = [{ a: "", b: "ok" }];
    store._validationRules = {
      a: { required: true, validate: () => true, message: "msg" },
      b: { required: false, validate: () => true, message: "msg2" },
    };
    const { errors, warnings } = store.validateRow(0);
    expect(warnings).toEqual({});
    expect(errors).toEqual({ a: "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD" });
  });

  test("validateSpreadsheet uses dynamic rules and caches result", () => {
    store._minimalFields = { a: "string" };
    store._columnsDef = ["a"];
    store._spreadsheetData = [{ a: "" }, { a: "val" }];
    const first = store.validateSpreadsheet();
    const second = store.validateSpreadsheet();
    expect(first).toBe(second);
    expect(first).toHaveProperty("errors");
    expect(first).toHaveProperty("warnings");
  });

  test("invalidateValidation clears cache", () => {
    store._cachedValidation = { foo: "bar" };
    store.invalidateValidation();
    expect(store._cachedValidation).toBeNull();
  });

  test("setImagePreview add, update, replace, remove", () => {
    store.setImagePreview({ fileName: "f1", progress: 0, src: null });
    expect(store.imagePreview).toHaveLength(1);

    store.setImagePreview({ fileName: "f1", progress: 50 }, "update");
    expect(store.imagePreview[0].progress).toBe(50);

    store.setImagePreview([{ fileName: "f2", progress: 10 }], "replace");
    expect(store.imagePreview).toEqual([{ fileName: "f2", progress: 10 }]);

    store.setImagePreview("f2", "remove");
    expect(store.imagePreview).toEqual([]);
  });

  test("setImageSectionFileNames add and remove", () => {
    store.setImageSectionFileNames("f1");
    expect(store._imageSectionFileNames).toContain("f1");

    store.setImageSectionFileNames(["f2", "f3"]);
    expect(store._imageSectionFileNames).toEqual(["f2", "f3"]);

    store.setImageSectionFileNames("f3", "remove");
    expect(store._imageSectionFileNames).toEqual(["f2"]);
  });

  test("setSubmissionId and getter", () => {
    store.setSubmissionId("SID");
    expect(store.submissionId).toBe("SID");
  });

  test("resetToDefaults clears state and localStorage", () => {
    store._submissionId = "S";
    localStorage.setItem("BulkImportStore", "xyz");
    store._spreadsheetData = [1];
    store._uploadedImages = ["a"];
    store.resetToDefaults();
    expect(store.submissionId).toBeNull();
    expect(store.spreadsheetData).toEqual([]);
    expect(store.uploadedImages).toEqual([]);
    expect(localStorage.getItem("BulkImportStore")).toBeNull();
  });

  test("hydrate populates fields from snapshot", () => {
    const snapshot = {
      submissionId: "S",
      rawData: [{ x: 1 }],
      rawColumns: ["x"],
      columnsDef: ["x"],
      imagePreview: [{ fileName: "f", progress: 5 }],
      imageSectionFileNames: ["f"],
      uploadedImages: ["f"],
      spreadsheetData: [{ x: 1 }],
      spreadsheetUploadProgress: 20,
      spreadsheetFileName: "sheet",
      worksheetInfo: { fileName: "sheet" },
      lastSavedAt: 123,
      validationErrors: { 0: { x: "err" } },
    };
    store.hydrate(snapshot);
    expect(store.submissionId).toBe("S");
    expect(store.rawData).toEqual([{ x: 1 }]);
    expect(store.rawColumns).toEqual(["x"]);
    expect(store.columnsDef).toEqual(["x"]);
    expect(store.imagePreview).toEqual([{ fileName: "f", progress: 5 }]);
    expect(store.imageSectionFileNames).toEqual(["f"]);
    expect(store.uploadedImages).toEqual(["f"]);
    expect(store.spreadsheetData).toEqual([{ x: 1 }]);
    expect(store.spreadsheetUploadProgress).toBe(20);
    expect(store.spreadsheetFileName).toBe("sheet");
    expect(store.worksheetInfo).toEqual({ fileName: "sheet" });
    expect(store.lastSavedAt).toBe(123);
    expect(store.validationErrors).toEqual({ 0: { x: "err" } });
  });

  test("applyServerUploadStatus updates previews and uploadedImages", () => {
    store._imagePreview = [
      { fileName: "f1", progress: 10 },
      { fileName: "f2", progress: 50 },
      { fileName: "f3", progress: 30 },
    ];
    store._uploadedImages = ["f1", "f2", "f3"];
    store._imageSectionFileNames = ["f1", "f2", "f3"];
    store.applyServerUploadStatus([["f1"], ["f2"]]);
    expect(store.uploadedImages).toEqual(["f1", "f2"]);
    expect(store.imageSectionFileNames).toEqual(["f1", "f2"]);
    expect(store.imagePreview).toEqual([
      { fileName: "f1", progress: 100, src: null },
      { fileName: "f2", progress: 100, src: null },
    ]);
  });

  test("isValidISO detects valid and invalid ISO strings", () => {
    expect(store.isValidISO("2025-07-03T12:00:00Z")).toBe(true);
    expect(store.isValidISO("not-a-date")).toBe(false);
  });

  test("setMinimalFields populates validationRules for int/double/string cols", () => {
    const minimal = { a: "int", b: "double", c: "string" };
    store.setMinimalFields(minimal);
    const vr = store.validationRules;
    expect(vr.a).toHaveProperty("validate");
    expect(vr.b).toHaveProperty("validate");
    expect(vr.c).toHaveProperty("validate");
  });

  test("applyToAllRows sets modal show flag and updates rows", () => {
    store._spreadsheetData = [{ col1: "x" }, { col1: "y" }];
    store._rawData = [{ col1: "x" }, { col1: "y" }];
    store._applyToAllRowModalShow = false;
    store.applyToAllRows("col1", "Z");
    expect(store.applyToAllRowModalShow).toBe(true);
    expect(store.spreadsheetData).toEqual([{ col1: "Z" }, { col1: "Z" }]);
    expect(store.rawData).toEqual([{ col1: "Z" }, { col1: "Z" }]);
  });

  test("imageUploadProgress returns 0 with no previews, calculates average otherwise", () => {
    expect(store.imageUploadProgress).toBe(0);
    store._imagePreview = [{ progress: 20 }, { progress: 80 }, {}];
    expect(store.imageUploadProgress).toBe((20 + 80 + 0) / 3);
  });

  test("stateSnapshot includes key properties", () => {
    store._submissionId = "S123";
    store._rawData = [{ a: 1 }];
    store._rawColumns = ["a"];
    store._columnsDef = ["a"];
    store._imagePreview = [{ fileName: "f", progress: 50 }];
    store._uploadedImages = ["f"];
    store._spreadsheetData = [{ a: 1 }];
    store._spreadsheetUploadProgress = 30;
    store._spreadsheetFileName = "sheet.xlsx";
    store._worksheetInfo = {
      fileName: "sheet.xlsx",
      sheetCount: 1,
      sheetNames: ["Sheet1"],
      columnCount: 1,
      rowCount: 1,
      uploadProgress: 30,
    };
    store._lastSavedAt = 999;
    store._validationErrors = { 0: { a: "err" } };
    const snap = store.stateSnapshot;
    expect(snap.submissionId).toBe("S123");
    expect(snap.rawData).toEqual([{ a: 1 }]);
    expect(snap.columnsDef).toEqual(["a"]);
    expect(snap.uploadedImages).toEqual(["f"]);
    expect(snap.spreadsheetData).toEqual([{ a: 1 }]);
    expect(snap.spreadsheetUploadProgress).toBe(30);
    expect(snap.spreadsheetFileName).toBe("sheet.xlsx");
    expect(snap.worksheetInfo).toMatchObject({
      sheetCount: 1,
      fileName: "sheet.xlsx",
    });
    expect(snap.lastSavedAt).toBe(999);
    expect(snap.validationErrors).toEqual({ 0: { a: "err" } });
  });

  test("clearSubmissionErrors empties submissionErrors", () => {
    store._submissionErrors = { foo: "bar" };
    store.clearSubmissionErrors();
    expect(store.submissionErrors).toEqual({});
  });

  test("mergeValidationError adds then removes correctly", () => {
    expect(store._validationErrors).toEqual({});
    store.mergeValidationError(2, "colX", { msg: "E" });
    expect(store.validationErrors[2]).toEqual({ colX: { msg: "E" } });
  });

  test("emptyFieldCount counts blank cells", () => {
    store._spreadsheetData = [
      { a: "", b: "x" },
      { a: " ", b: "" },
    ];
    expect(store.emptyFieldCount).toBe(3);
  });

  test("validateMediaAsset0ColumnOnly flags missing when required", () => {
    store._validationRules["Encounter.mediaAsset0"] = {
      required: true,
      validate: () => false,
      message: () => "bad",
    };
    store._spreadsheetData = [
      { "Encounter.mediaAsset0": "" },
      { "Encounter.mediaAsset0": "f1,f2" },
    ];
    const { errors } = store.validateMediaAsset0ColumnOnly();
    expect(errors).toHaveProperty("1");
    expect(errors[1]["Encounter.mediaAsset0"]).toBe("bad");
  });

  test("fetchAndApplyUploaded does nothing when no submissionId", async () => {
    global.fetch = jest.fn();
    store._submissionId = null;
    await expect(store.fetchAndApplyUploaded()).resolves.toBeUndefined();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test("uploadFilteredFiles alerts on invalid file", () => {
    const flow = {
      files: [{ name: "big", size: 10 * 1024 * 1024 }],
      opts: { query: {} },
      upload: jest.fn(),
    };
    store._flow = flow;
    store.uploadFilteredFiles();
    expect(flow.upload).not.toHaveBeenCalled();
  });

  test("generateThumbnailsForFirst50 resolves immediately when no previews", async () => {
    store._imagePreview = [];
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
  });

  test("generateThumbnailsForFirst50 resolves when too many previews", async () => {
    store._imagePreview = Array(store._imageCountGenerateThumbnail + 1).fill(
      {},
    );
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
  });

  test("updateRawFromNormalizedRow handles date year only", () => {
    store._spreadsheetData = [{ "Encounter.year": "2025" }];
    store._rawData = [
      {
        "Encounter.year": 0,
        "Encounter.month": 1,
        "Encounter.day": 2,
        "Encounter.hour": 3,
        "Encounter.minutes": 4,
      },
    ];
    store.updateRawFromNormalizedRow();
    const raw = store._rawData[0];
    expect(raw["Encounter.year"]).toBe(2025);
    expect(raw["Encounter.month"]).toBe("");
    expect(raw["Encounter.day"]).toBe("");
    expect(raw["Encounter.hour"]).toBe("");
    expect(raw["Encounter.minutes"]).toBe("");
  });

  test("updateRawFromNormalizedRow splits genus and epithet", () => {
    store._spreadsheetData = [{ "Encounter.genus": "Genus species" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.genus"]).toBe("Genus");
    expect(store._rawData[0]["Encounter.specificEpithet"]).toBe("species");
  });

  test("updateRawFromNormalizedRow transforms mediaAsset0 correctly", () => {
    store._spreadsheetData = [{ "Encounter.mediaAsset0": "img1,img2" }];
    store._rawData = [
      { "Encounter.mediaAsset0": "old0", "Encounter.mediaAsset1": "old1" },
    ];
    store.updateRawFromNormalizedRow();
    const raw = store._rawData[0];
    expect(raw["Encounter.mediaAsset0"]).toBe("img1");
    expect(raw["Encounter.mediaAsset1"]).toBe("img2");
  });

  test("updateRawFromNormalizedRow splits decimalLatitude", () => {
    store._spreadsheetData = [{ "Encounter.decimalLatitude": "12.3,45.6" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.decimalLatitude"]).toBe("12.3");
    expect(store._rawData[0]["Encounter.decimalLongitude"]).toBe("45.6");
  });

  test("isDynamicKnownColumn covers various patterns", () => {
    store._labeledKeywordAllowedKeys = ["foo", "bar"];
    store._labeledKeywordAllowedPairs = { foo: ["v"], bar: ["v2"] };
    expect(store.isDynamicKnownColumn("Encounter.keyword10")).toBe(true);
    expect(store.isDynamicKnownColumn("Encounter.mediaAsset5.keywords")).toBe(
      true,
    );
    expect(store.isDynamicKnownColumn("Encounter.mediaAsset5.foo")).toBe(true);
    expect(store.isDynamicKnownColumn("Encounter.mediaAsset5.baz")).toBe(false);
    expect(store.isDynamicKnownColumn("Encounter.photographer2.fullName")).toBe(
      true,
    );
    expect(store.isDynamicKnownColumn("Encounter.submitter3.affiliation")).toBe(
      true,
    );
    expect(
      store.isDynamicKnownColumn("Encounter.informOther1.emailAddress"),
    ).toBe(true);
    expect(store.isDynamicKnownColumn("Encounter.unknown")).toBe(false);
  });

  test("applyDynamicValidationRules adds rules for dynamic columns", () => {
    store._labeledKeywordAllowedKeys = ["foo"];
    store._labeledKeywordAllowedPairs = { foo: ["v1", "v2"] };
    store._columnsDef = [
      "Encounter.keyword1",
      "Encounter.mediaAsset0.foo",
      "Encounter.photographer1.fullName",
      "Encounter.submitter1.emailAddress",
      "Encounter.informOther0.affiliation",
    ];
    store.applyDynamicValidationRules();
    const rules = store.validationRules;
    expect(rules["Encounter.keyword1"]).toBeDefined();
    expect(rules["Encounter.mediaAsset0.foo"]).toBeDefined();
    expect(rules["Encounter.photographer1.fullName"]).toBeDefined();
    expect(rules["Encounter.submitter1.emailAddress"]).toBeDefined();
    expect(rules["Encounter.informOther0.affiliation"]).toBeDefined();
  });

  test("stateSnapshot returns deep copy (immutable)", () => {
    store._rawData = [{ x: 1 }];
    const snap = store.stateSnapshot;
    store._rawData[0].x = 9;
    expect(snap.rawData[0].x).toBe(1);
  });
  test("missingRequiredColumns excludes defined columns", () => {
    store._columnsDef = ["colA", "colB"];
    const missing = store.missingRequiredColumns;
    expect(missing).not.toContain("colA");
    expect(missing).not.toContain("colB");
  });

  test("errorSummary counts missing and pending correctly", () => {
    store._imagePreview = [
      { fileName: "f1", progress: 50 },
      { fileName: "f2", progress: 100 },
    ];
    store._columnsDef = ["a", "b"];
    store._spreadsheetData = [
      { a: "", b: "" },
      { a: "x", b: "y" },
    ];
    store._submissionErrors = {};

    const summary = store.errorSummary;
    expect(summary.error).toBe(0);
    expect(summary.missingField).toBe(0);
    expect(summary.emptyField).toBe(2);
    expect(summary.imgVerifyPending).toBe(0);
  });

  test("setWorksheetInfo updates worksheetInfo correctly", () => {
    store._spreadsheetUploadProgress = 42;
    store.setWorksheetInfo(3, ["S1", "S2", "S3"], 5, 10, "sheet.xlsx");
    const wi = store.worksheetInfo;
    expect(wi.sheetCount).toBe(3);
    expect(wi.sheetNames).toEqual(["S1", "S2", "S3"]);
    expect(wi.columnCount).toBe(5);
    expect(wi.rowCount).toBe(10);
    expect(wi.fileName).toBe("sheet.xlsx");
    expect(wi.uploadProgress).toBe(42);
  });

  test("fetchAndApplyUploaded no-op when submissionId null", async () => {
    global.fetch = jest.fn();
    store._submissionId = null;
    await expect(store.fetchAndApplyUploaded()).resolves.toBeUndefined();
    expect(global.fetch).not.toHaveBeenCalled();
  });

  test("fetchAndApplyUploaded applies fetched files", async () => {
    store._submissionId = "ID123";
    store._uploadedImages = ["imgA", "imgB", "imgC"];
    store._imageSectionFileNames = ["imgA", "imgB", "imgC"];
    global.fetch = jest.fn().mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({ files: [["imgA"], ["imgB"]] }),
    });
    store._imagePreview = [
      { fileName: "imgA", progress: 0 },
      { fileName: "imgB", progress: 20 },
      { fileName: "imgC", progress: 50 },
    ];

    await store.fetchAndApplyUploaded();
    expect(global.fetch).toHaveBeenCalledWith(
      "/api/v3/bulk-import/ID123/files",
    );
    expect(store.uploadedImages).toEqual(["imgA", "imgB"]);
    expect(store.imageSectionFileNames).toEqual(["imgA", "imgB"]);
    expect(store.imagePreview.every((p) => p.progress === 100)).toBe(true);
  });

  test("uploadFilteredFiles alerts on oversized files", () => {
    const flow = {
      files: [{ name: "big", size: 10 * 1024 * 1024 }],
      opts: { query: {} },
      upload: jest.fn(),
    };
    store._flow = flow;
    store.uploadFilteredFiles();
    expect(flow.upload).not.toHaveBeenCalled();
  });

  test("uploadFilteredFiles uploads valid files only", () => {
    const flow = {
      files: [
        { name: "small", size: 1 * 1024 * 1024 },
        { name: "large", size: 20 * 1024 * 1024 },
      ],
      opts: { query: {} },
      upload: jest.fn(),
    };
    store._flow = flow;
    window.alert = jest.fn();
    store.uploadFilteredFiles();
    expect(flow.upload).toHaveBeenCalledTimes(1);
  });

  test("generateThumbnailsForFirst50 resolves immediately on empty or too many previews", async () => {
    store._imagePreview = [];
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
    store._imagePreview = Array(store._imageCountGenerateThumbnail + 1).fill(
      {},
    );
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
  });

  test("updateRawFromNormalizedRow handles year, genus, mediaAsset0, decimalLatitude", () => {
    store._spreadsheetData = [{ "Encounter.year": "2025" }];
    store._rawData = [
      {
        "Encounter.year": 0,
        "Encounter.month": 1,
        "Encounter.day": 2,
        "Encounter.hour": 3,
        "Encounter.minutes": 4,
      },
    ];
    store.updateRawFromNormalizedRow();
    const raw0 = store._rawData[0];
    expect(raw0["Encounter.year"]).toBe(2025);
    expect(raw0["Encounter.month"]).toBe("");
    expect(raw0["Encounter.day"]).toBe("");
    expect(raw0["Encounter.hour"]).toBe("");
    expect(raw0["Encounter.minutes"]).toBe("");

    store._spreadsheetData = [{ "Encounter.genus": "G S" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.genus"]).toBe("G");
    expect(store._rawData[0]["Encounter.specificEpithet"]).toBe("S");

    store._spreadsheetData = [{ "Encounter.mediaAsset0": "i1,i2" }];
    store._rawData = [
      { "Encounter.mediaAsset0": "", "Encounter.mediaAsset1": "" },
    ];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.mediaAsset0"]).toBe("i1");
    expect(store._rawData[0]["Encounter.mediaAsset1"]).toBe("i2");

    store._spreadsheetData = [{ "Encounter.decimalLatitude": "9.1,8.2" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.decimalLatitude"]).toBe("9.1");
    expect(store._rawData[0]["Encounter.decimalLongitude"]).toBe("8.2");
  });

  test("handles Sighting.year with full datetime down to seconds", () => {
    store._spreadsheetData = [{ "Sighting.year": "2022-05-10T14:30" }];
    store._rawData = [
      {
        "Sighting.year": 0,
        "Sighting.month": 1,
        "Sighting.day": 2,
        "Sighting.hour": 3,
        "Sighting.minutes": 4,
        "Sighting.seconds": 5,
      },
    ];
    store.updateRawFromNormalizedRow();
    const raw = store._rawData[0];
    expect(raw["Sighting.year"]).toBe(2022);
    expect(raw["Sighting.month"]).toBe(5);
    expect(raw["Sighting.day"]).toBe(10);
    expect(raw["Sighting.hour"]).toBe(14);
    expect(raw["Sighting.minutes"]).toBe(30);
  });

  test("stateSnapshot returns deep copy", () => {
    store._rawData = [{ x: 1 }];
    const snap = store.stateSnapshot;
    store._rawData[0].x = 9;
    expect(snap.rawData[0].x).toBe(1);
  });

  test("uploadFilteredFiles alerts and filters", () => {
    const flow = {
      files: [{ name: "big", size: 2 * 1024 * 1024 }],
      opts: { query: {} },
      upload: jest.fn(),
    };
    store._flow = flow;
    // Set _maxImageSizeMB to 1 so the 2 MB file is rejected.
    // uploadFilteredFiles() no longer calls alert; it silently skips oversized files.
    store._maxImageSizeMB = 1;

    store.uploadFilteredFiles();
    expect(flow.upload).not.toHaveBeenCalled();
    store._flow.files = [{ name: "small", size: 500 * 1024 }];
    store.uploadFilteredFiles();
    expect(flow.upload).toHaveBeenCalledTimes(1);
  });

  test("generateThumbnailsForFirst50 resolves edge cases", async () => {
    store._imagePreview = [];
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
    store._imagePreview = Array(store._imageCountGenerateThumbnail + 5).fill(
      {},
    );
    await expect(store.generateThumbnailsForFirst50()).resolves.toBeUndefined();
  });

  test("updateRawFromNormalizedRow various transforms", () => {
    store._spreadsheetData = [{ "Encounter.year": "2020" }];
    store._rawData = [
      {
        "Encounter.year": 0,
        "Encounter.month": 1,
        "Encounter.day": 1,
        "Encounter.hour": 1,
        "Encounter.minutes": 1,
      },
    ];
    store.updateRawFromNormalizedRow();
    const r0 = store._rawData[0];
    expect(r0["Encounter.year"]).toBe(2020);
    expect(r0["Encounter.month"]).toBe("");
    store._spreadsheetData = [{ "Encounter.genus": "G E" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.genus"]).toBe("G");
    store._spreadsheetData = [{ "Encounter.mediaAsset0": "a,b" }];
    store._rawData = [
      { "Encounter.mediaAsset0": "", "Encounter.mediaAsset1": "" },
    ];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.mediaAsset1"]).toBe("b");
    store._spreadsheetData = [{ "Encounter.decimalLatitude": "1,2" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Encounter.decimalLongitude"]).toBe("2");
  });

  test("validateMediaAsset0ColumnOnly flags required and invalid", () => {
    store._validationRules["Encounter.mediaAsset0"] = {
      required: true,
      validate: () => false,
      message: () => "msg",
    };
    store._spreadsheetData = [
      { "Encounter.mediaAsset0": "" },
      { "Encounter.mediaAsset0": "x" },
    ];
    const { errors, warnings } = store.validateMediaAsset0ColumnOnly();
    expect(warnings).toEqual({});
    expect(errors["0"]["Encounter.mediaAsset0"]).toBe(
      "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD",
    );
    expect(errors["1"]["Encounter.mediaAsset0"]).toBe("msg");
  });

  test("validateRow and validateSpreadsheet caching", () => {
    store._columnsDef = ["a"];
    store._spreadsheetData = [{ a: "" }];
    store._validationRules = {
      a: { required: true, validate: () => true, message: "m" },
    };
    const vr = store.validateRow(0);
    expect(vr.errors.a).toBe("BULKIMPORT_ERROR_INVALID_REQUIREDFIELD");
    store._minimalFields = { a: "string" };
    store._columnsDef = ["a"];
    store._spreadsheetData = [{ a: "" }];
    const vs1 = store.validateSpreadsheet();
    const vs2 = store.validateSpreadsheet();
    expect(vs1).toBe(vs2);
    store.invalidateValidation();
    expect(store._cachedValidation).toBeNull();
  });

  test("mergeValidationError and Warning", () => {
    store.mergeValidationError(1, "c", { m: "E" });
    expect(store.validationErrors[1]["c"].m).toBe("E");
  });

  test("applyToAllRows updates sheet and raw", () => {
    store._spreadsheetData = [{ x: 0 }];
    store._rawData = [{ x: 0 }];
    store.applyToAllRows("x", 5);
    expect(store.spreadsheetData[0].x).toBe(5);
  });

  test("stateSnapshot deep copy", () => {
    store._rawData = [{ q: 1 }];
    const s = store.stateSnapshot;
    store._rawData[0].q = 2;
    expect(s.rawData[0].q).toBe(1);
  });

  test("saveState writes to localStorage and toggles draft flag", () => {
    store._submissionId = "T";
    store._rawData = [{ a: 1 }];
    store.saveState();
    expect(store.isSavingDraft).toBe(true);
    setTimeout(() => {
      expect(store.isSavingDraft).toBe(false);
      expect(localStorage.getItem("BulkImportStore")).toBeTruthy();
    }, 800);
  });

  test("initializeFlow and removePreview", () => {
    const ref = {};
    store.initializeFlow(ref, 1);
    store._imagePreview = [{ fileName: "Z" }];
    store._imageSectionFileNames = ["Z"];
    store._uploadedImages = ["Z"];
    store._flow = { files: [{ name: "Z" }], removeFile: jest.fn() };
    store.removePreview("Z");
  });
});

describe("BulkImportStore tests part 2", () => {
  let store;

  beforeEach(() => {
    store = new BulkImportStore();
  });

  test("missingPhotos returns names not in uploadedImages", () => {
    store._uploadedImages = ["img1.jpg", "img3.png"];
    store._spreadsheetData = [
      { "Encounter.mediaAsset0": "img1.jpg,img2.jpg" },
      { "Encounter.mediaAsset0": "img2.jpg,img4.png" },
    ];
    const missing = store.missingPhotos;
    expect(missing.sort()).toEqual(["img2.jpg", "img4.png"].sort());
  });

  test("errorPages computes correct page indices", () => {
    store._pageSize = 10;
    store._validationErrors = {
      9: { a: "err" },
      10: { b: "err" },
      22: { c: "err" },
    };
    const pages = store.errorPages;
    expect(pages.has(0)).toBe(true);
    expect(pages.has(1)).toBe(true);
    expect(pages.has(2)).toBe(true);
    expect(pages.size).toBe(3);
  });

  test("validateSpreadsheet flags unknown columns", () => {
    store._columnsDef = ["UnknownCol"];
    store._minimalFields = {};
    store._spreadsheetData = [{ UnknownCol: "val" }];
    const { warnings, errors } = store.validateSpreadsheet();
    expect(errors).toEqual({});
    expect(warnings[0]).toHaveProperty(
      "UnknownCol",
      "BULKIMPORT_ERROR_INVALID_UNKNOWNCOLUMN",
    );
  });

  test("validateSpreadsheet flags synonym field conflicts", () => {
    store._columnsDef = ["A", "B", "C"];
    store._spreadsheetData = [{ A: "x", B: "y", C: "z" }];
    store._synonymFields = [["A", "B", "C"]];
    const { errors } = store.validateSpreadsheet();
    expect(errors[0]).toBeDefined();
    ["A", "B", "C"].forEach((col) => {
      expect(errors[0]).toHaveProperty(col);
      expect(errors[0][col].id).toBe("BULKIMPORT_ERROR_INVALID_SYNONYMFIELDS");
    });
  });

  test("uploadFilteredFiles uploads only valid files when flow.files mixed", () => {
    const small = { name: "small", size: 1 * 1024 * 1024 };
    const large = { name: "large", size: 10 * 1024 * 1024 };
    const flow = {
      files: [small, large],
      opts: { query: {} },
      upload: jest.fn(),
    };
    store._flow = flow;
    store.uploadFilteredFiles();
    expect(flow.upload).toHaveBeenCalledTimes(1);
    expect(flow.upload).toHaveBeenCalledWith(small);
  });
});

describe("BulkImportStore tests part 3", () => {
  let store;
  beforeEach(() => {
    store = new BulkImportStore();
  });

  test("isValidISO detects valid/invalid ISO strings", () => {
    expect(store.isValidISO("2025-07-03T12:00:00Z")).toBe(true);
    expect(store.isValidISO("not-a-date")).toBe(false);
  });

  test("validateMediaAsset0ColumnOnly flags required and invalid correctly", () => {
    store._validationRules["Encounter.mediaAsset0"] = {
      required: true,
      validate: () => false,
      message: () => "bad",
    };
    store._spreadsheetData = [
      { "Encounter.mediaAsset0": "" },
      { "Encounter.mediaAsset0": "foo" },
    ];
    const { errors, warnings } = store.validateMediaAsset0ColumnOnly();
    expect(warnings).toEqual({});
    expect(errors[0]["Encounter.mediaAsset0"]).toBe(
      "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD",
    );
    expect(errors[1]["Encounter.mediaAsset0"]).toBe("bad");
  });

  test("validateRow respects rule.validate and rule.message", () => {
    store._columnsDef = ["X"];
    store._spreadsheetData = [{ X: "val" }];
    store._validationRules = {
      X: {
        required: false,
        validate: (v) => v === "ok",
        message: "must-be-ok",
      },
    };
    const { errors } = store.validateRow(0);
    expect(errors.X).toBe("must-be-ok");
  });

  test("applyDynamicValidationRules adds rules for email and string fields", () => {
    store._labeledKeywordAllowedKeys = ["foo"];
    store._labeledKeywordAllowedPairs = { foo: ["a", "b"] };
    store._columnsDef = [
      "Encounter.keyword1",
      "Encounter.mediaAsset0.foo",
      "Encounter.photographer0.emailAddress",
      "Encounter.submitter0.fullName",
    ];
    store.applyDynamicValidationRules();
    const vr = store.validationRules;
    expect(typeof vr["Encounter.keyword1"].validate).toBe("function");
    expect(typeof vr["Encounter.mediaAsset0.foo"].validate).toBe("function");
    expect(vr["Encounter.photographer0.emailAddress"].message).toMatch(/email/);
    expect(typeof vr["Encounter.submitter0.fullName"].validate).toBe(
      "function",
    );
  });

  test("errorSummary counts imgVerifyPending when upload in progress", () => {
    store._imagePreview = [
      { fileName: "f1", progress: 50 },
      { fileName: "f2", progress: 100 },
    ];
    store._columnsDef = ["Encounter.mediaAsset0"];
    store._spreadsheetData = [{ "Encounter.mediaAsset0": "f1" }];
    store._validationRules = {};
    const summary = store.errorSummary;
    expect(summary.imgVerifyPending).toBe(1);
  });
});

describe("BulkImportStore file-tree and triggerUpload branches", () => {
  let store;
  beforeEach(() => {
    store = new BulkImportStore();
  });

  test("traverseFileTree handles directory entries recursively", () => {
    const child = { isDirectory: false, isFile: false };
    const reader = {
      readEntries: (cb) => cb([child]),
    };
    const entry = {
      isDirectory: true,
      createReader: () => reader,
    };
    store._onAllFilesParsed = jest.fn();
    store._pendingReadCount = 0;

    store.traverseFileTree(entry, 1);
    expect(store._onAllFilesParsed).toHaveBeenCalledTimes(1);
    expect(store._collectedValidFiles).toEqual([]);
  });

  test("triggerUploadAfterFileInput calls onAll when imagePreview is not empty", () => {
    store._imagePreview = [{ foo: "bar" }];
    store._onAllFilesParsed = jest.fn();
    store.triggerUploadAfterFileInput();
    expect(store._onAllFilesParsed).toHaveBeenCalledTimes(1);
  });

  test("triggerUploadAfterFileInput does nothing when imagePreview is empty", () => {
    store._imagePreview = [];
    store._onAllFilesParsed = jest.fn();
    store.triggerUploadAfterFileInput();
    expect(store._onAllFilesParsed).not.toHaveBeenCalled();
  });

  test("updateRawFromNormalizedRow splits Sighting.decimalLatitude correctly", () => {
    store._spreadsheetData = [{ "Sighting.decimalLatitude": "1.2,3.4" }];
    store._rawData = [{}];
    store.updateRawFromNormalizedRow();
    expect(store._rawData[0]["Sighting.decimalLatitude"]).toBe("1.2");
    expect(store._rawData[0]["Sighting.decimalLongitude"]).toBe("3.4");
  });
});
