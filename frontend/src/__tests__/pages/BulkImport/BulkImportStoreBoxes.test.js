import { act, renderHook } from "@testing-library/react";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import usePostBulkImport from "../../../models/bulkImport/usePostBulkImport";
import { client } from "../../../api/client";

jest.mock("@flowjs/flow.js", () =>
  jest.fn().mockImplementation(() => ({
    opts: { query: {} },
    assignBrowse: jest.fn(),
    on: jest.fn(),
    files: [],
  })),
);
jest.mock("../../../api/client", () => ({ client: { post: jest.fn() } }));

const A0 = "Encounter.mediaAsset0";
const A2 = "Encounter.mediaAsset2";
const CAPABLE = ["Encounter.mediaAsset#", "Encounter.mediaAsset#.bbox"];

const boxedStore = (rows, extraCols = []) => {
  const store = new BulkImportStore();
  const cols = [
    "Encounter.genus",
    A0,
    `${A0}.bbox`,
    `${A0}.iaClass`,
    `${A0}.viewpoint`,
    `${A0}.theta`,
    A2,
    `${A2}.bbox`,
    ...extraCols,
  ];
  store.setIndexableFields(CAPABLE);
  store._columnsDef = cols;
  store._rawColumns = [...cols];
  store._spreadsheetData = rows.map((r) => ({ ...r }));
  store._rawData = rows.map((r) => ({ ...r }));
  store.setValidViewpoints(["left", "right"]);
  store.setMatchableClasses({ "Giraffa giraffa": ["giraffe_whole"] });
  return store;
};

describe("BulkImportStore with box columns", () => {
  test("missing photos cover every slot", () => {
    const store = boxedStore([{ [A0]: "a.jpg", [A2]: "c.jpg" }]);
    store._uploadedImages = ["a.jpg"];
    expect(store.missingPhotos).toEqual(["c.jpg"]);
    const { errors } = store.validateMediaAssetColumnsOnly();
    expect(errors[0][A2]).toBe("MISSINGc.jpg");
    expect(errors[0][A0]).toBeUndefined();
  });

  test("bare slots and box suffixes are known columns", () => {
    const store = boxedStore([]);
    expect(store.isDynamicKnownColumn("Encounter.mediaAsset4")).toBe(true);
    expect(store.isDynamicKnownColumn("Encounter.mediaAsset4.bboxSource")).toBe(
      true,
    );
  });

  test("box values are checked and blanks pass", () => {
    const store = boxedStore([
      {
        [`${A0}.bbox`]: "1,2,3",
        [`${A0}.theta`]: "7",
        [`${A0}.viewpoint`]: "up",
      },
      { [`${A0}.bbox`]: "", [`${A0}.theta`]: "", [`${A0}.viewpoint`]: "" },
      { [`${A0}.viewpoint`]: " left " },
    ]);
    const { errors } = store.validateSpreadsheet();
    expect(errors[0][`${A0}.bbox`]).toBe("BULKIMPORT_ERROR_INVALID_BBOX");
    expect(errors[0][`${A0}.theta`]).toBe("BULKIMPORT_ERROR_INVALID_THETA");
    expect(errors[0][`${A0}.viewpoint`]).toBe(
      "BULKIMPORT_ERROR_INVALID_VIEWPOINT",
    );
    const boxErrors = (r) =>
      Object.keys(errors[r] || {}).filter((c) => c.startsWith(A0));
    expect(boxErrors(1)).toEqual([]);
    expect(boxErrors(2)).toEqual([]);
  });

  test("a labeled keyword named like a box suffix does not take over", () => {
    const store = boxedStore([{ [`${A0}.viewpoint`]: "up" }]);
    store.setLabeledKeywordAllowedKeys(["viewpoint"]);
    store.setLabeledKeywordAllowedPairs({ viewpoint: ["up"] });
    const { errors } = store.validateSpreadsheet();
    expect(errors[0][`${A0}.viewpoint`]).toBe(
      "BULKIMPORT_ERROR_INVALID_VIEWPOINT",
    );
  });

  test("an unmatchable class warns unless a skip box is ticked", () => {
    const store = boxedStore([
      { "Encounter.genus": "Giraffa giraffa", [`${A0}.iaClass`]: "zebra" },
      { "Encounter.genus": "", [`${A0}.iaClass`]: "zebra" },
      { "Encounter.genus": "Unknown thing", [`${A0}.iaClass`]: "zebra" },
    ]);
    const warning = {
      id: "BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE",
      values: { allowed: "giraffe_whole" },
    };
    store.revalidate();
    expect(store.validationWarnings[0][`${A0}.iaClass`]).toEqual(warning);
    expect(store.validationWarnings[1]).toBeUndefined();
    expect(store.validationWarnings[2]).toBeUndefined();

    store.setSkipDetection(true);
    expect(store.validationWarnings[0]).toBeUndefined();
    store.setSkipDetection(false);
    expect(store.validationWarnings[0][`${A0}.iaClass`]).toEqual(warning);
    store.setSkipIdentification(true);
    expect(store.validationWarnings[0]).toBeUndefined();
    store.setSkipIdentification(false);
    expect(store.validationWarnings[0][`${A0}.iaClass`]).toEqual(warning);
  });

  test("a boxed photo cell is one exact filename, commas included", () => {
    const store = boxedStore([{ [A0]: "a,b.jpg", [A2]: "c.jpg" }]);
    store._uploadedImages = ["a,b.jpg"];
    expect(store.missingPhotos).toEqual(["c.jpg"]);
    expect(store.photoCellValid("a,b.jpg")).toBe(true);
    expect(store.photoCellMessage("c.jpg")).toBe("MISSINGc.jpg");
  });

  test("a rule left by an earlier un-boxed sheet does not reject bboxSource", () => {
    const store = new BulkImportStore();
    store.setIndexableFields(CAPABLE);
    store._columnsDef = [A0, `${A0}.bboxSource`];
    store._spreadsheetData = [{ [A0]: "a.jpg", [`${A0}.bboxSource`]: "md-v5" }];
    store.applyDynamicValidationRules();
    expect(store._validationRules[`${A0}.bboxSource`]).toBeDefined();

    store._columnsDef = [A0, `${A0}.bbox`, `${A0}.bboxSource`];
    store._spreadsheetData = [
      {
        [A0]: "a.jpg",
        [`${A0}.bbox`]: "1,1,5,5",
        [`${A0}.bboxSource`]: "md-v5",
      },
    ];
    store.revalidate();
    expect(store.validationErrors[0]?.[`${A0}.bboxSource`]).toBeUndefined();
  });

  test("changing the species refreshes the class warning", () => {
    const store = boxedStore([
      { "Encounter.genus": "Equus quagga", [`${A0}.iaClass`]: "giraffe_whole" },
    ]);
    store.setMatchableClasses({
      "Giraffa giraffa": ["giraffe_whole"],
      "Equus quagga": ["zebra_plains"],
    });
    store.revalidate();
    expect(store.validationWarnings[0]?.[`${A0}.iaClass`]).toBeDefined();
    store.updateCellValue(0, "Encounter.genus", "Giraffa giraffa");
    store.revalidate();
    expect(store.validationWarnings[0]).toBeUndefined();
  });

  test("un-boxed photos are counted only when some box is filled in", () => {
    const store = boxedStore([
      { [A0]: "a.jpg", [`${A0}.bbox`]: "1,1,5,5", [A2]: "b.jpg" },
    ]);
    expect(store.unboxedPhotoCount).toBe(1);
    const empty = boxedStore([{ [A0]: "a.jpg", [A2]: "b.jpg" }]);
    expect(empty.unboxedPhotoCount).toBe(0);
  });

  test("submit keeps slots and the payload carries them in place", async () => {
    const rows = [
      {
        [A0]: "a.jpg",
        [`${A0}.bbox`]: "1,1,5,5",
        [`${A0}.iaClass`]: "giraffe_whole",
        [A2]: "c.jpg",
        [`${A2}.bbox`]: "2,2,6,6",
      },
      { [A2]: "d.jpg", [`${A2}.bbox`]: "3,3,7,7" },
    ];
    const store = boxedStore(rows);
    store.updateRawFromNormalizedRow();
    expect(store.rawData[0]).toMatchObject(rows[0]);
    expect(store.rawData[1]).toMatchObject(rows[1]);
    expect(store.rawData[1][A0]).toBeUndefined();
    expect(store.rawColumns).not.toContain("Encounter.mediaAsset1");

    client.post.mockResolvedValue({ status: 200 });
    const { result } = renderHook(() => usePostBulkImport());
    await act(() =>
      result.current.submit(
        "id",
        store.rawColumns,
        store.rawData,
        "f.csv",
        [],
        false,
        false,
      ),
    );
    const { fieldNames, rows: sent } = client.post.mock.calls[0][1];
    const at = (r, f) => sent[r][fieldNames.indexOf(f)];
    expect(at(0, A0)).toBe("a.jpg");
    expect(at(0, `${A0}.iaClass`)).toBe("giraffe_whole");
    expect(at(0, A2)).toBe("c.jpg");
    expect(at(0, `${A2}.bbox`)).toBe("2,2,6,6");
    expect(at(1, A0)).toBeUndefined();
    expect(at(1, A2)).toBe("d.jpg");
    expect(fieldNames).not.toContain("Encounter.mediaAsset1");
  });

  test("an un-boxed merged cell is still renumbered", () => {
    const store = new BulkImportStore();
    store._columnsDef = [A0];
    store._rawColumns = [A0];
    store._spreadsheetData = [{ [A0]: "a.jpg, b.jpg" }];
    store._rawData = [{ [A0]: "a.jpg", "Encounter.mediaAsset1": "b.jpg" }];
    store.updateRawFromNormalizedRow();
    expect(store.rawData[0][A0]).toBe("a.jpg");
    expect(store.rawData[0]["Encounter.mediaAsset1"]).toBe("b.jpg");
  });

  test("without the site capability box columns stay unknown", () => {
    const store = boxedStore([{ [`${A0}.bbox`]: "1,2,3" }]);
    store.setIndexableFields([]);
    expect(store.isBoxedSheet).toBe(false);
    const { errors, warnings } = store.validateSpreadsheet();
    expect(warnings[0][`${A0}.bbox`]).toBe(
      "BULKIMPORT_ERROR_INVALID_UNKNOWNCOLUMN",
    );
    expect(errors[0]?.[`${A0}.bbox`]).toBeUndefined();
  });

  test("resetSpreadsheet clears the spreadsheet and keeps images", () => {
    const store = boxedStore([{ [A0]: "a.jpg" }]);
    store._uploadedImages = ["a.jpg"];
    store.setSpreadsheetUploadProgress(100);
    store.setSpreadsheetFileName("f.csv");
    store.resetSpreadsheet();
    expect(store.spreadsheetData).toEqual([]);
    expect(store.rawData).toEqual([]);
    expect(store.rawColumns).toEqual([]);
    expect(store.columnsDef).toEqual([]);
    expect(store.spreadsheetFileName).toBe("");
    expect(store.spreadsheetUploadProgress).toBe(0);
    expect(store.uploadedImages).toEqual(["a.jpg"]);
  });
});
