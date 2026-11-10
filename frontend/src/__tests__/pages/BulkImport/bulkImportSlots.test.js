import {
  boxSuffixOf,
  javaTrim,
  isLegacyBoxedDraft,
  hasBoxColumns,
  isPhotoSlotColumn,
  isSlotColumn,
  isValidBbox,
  isValidTheta,
} from "../../../pages/BulkImport/bulkImportSlots";

const CAPABLE = ["Encounter.mediaAsset#", "Encounter.mediaAsset#.bbox"];

describe("bulkImportSlots", () => {
  test("bbox accepts what the server's parseBbox accepts", () => {
    [
      "10, 20,30,40",
      "+1,-0,5,5",
      "0,0,2147483647,1",
      "０,０,５,５",
      "\u001c0,0,5,5",
      "",
      "  ",
    ].forEach((v) => expect(isValidBbox(v)).toBe(true));
  });

  test("bbox rejects what the server rejects", () => {
    [
      "1,2,3",
      "1,2,3,4,5",
      "1,,3,4",
      "-1,0,5,5",
      "0,-1,5,5",
      "0,0,0,5",
      "0,0,5,0",
      "1.5,0,5,5",
      "0,0,2147483648,1",
    ].forEach((v) => expect(isValidBbox(v)).toBe(false));
  });

  test("theta checks range and defers Java-only forms to the server", () => {
    ["6.2832", "-6.2832", "1e-3", "1D", "0x1.0p0", "", "\u001c"].forEach((v) =>
      expect(isValidTheta(v)).toBe(true),
    );
    ["6.3", "-7", "Infinity", "abc"].forEach((v) =>
      expect(isValidTheta(v)).toBe(false),
    );
  });

  test("boxed sheets need the site capability and a bbox column", () => {
    expect(hasBoxColumns(["Encounter.mediaAsset3.bbox"], CAPABLE)).toBe(true);
    expect(hasBoxColumns(["Encounter.mediaAsset3.bbox"], [])).toBe(false);
    expect(hasBoxColumns(["Encounter.mediaAsset3.bbox"], undefined)).toBe(
      false,
    );
    expect(hasBoxColumns(["Encounter.mediaAsset0.keywords"], CAPABLE)).toBe(
      false,
    );
  });

  test("javaTrim strips control characters like Java", () => {
    expect(javaTrim("\u001c left \t")).toBe("left");
    expect(javaTrim("\u00a0x")).toBe("\u00a0x");
  });

  test("column predicates", () => {
    expect(isPhotoSlotColumn("Encounter.mediaAsset12")).toBe(true);
    expect(isPhotoSlotColumn("Encounter.mediaAsset1.bbox")).toBe(false);
    expect(isSlotColumn("Encounter.mediaAsset2.keywords")).toBe(true);
    expect(isSlotColumn("Encounter.mediaAsset2.quality")).toBe(true);
    expect(isSlotColumn("Encounter.keyword0")).toBe(false);
    expect(boxSuffixOf("Encounter.mediaAsset1.theta")).toBe("theta");
    expect(boxSuffixOf("Encounter.mediaAsset1.keywords")).toBe(null);
  });

  test("legacy boxed drafts are recognised by shape", () => {
    const columnsDef = ["Encounter.mediaAsset0", "Encounter.mediaAsset0.bbox"];
    expect(
      isLegacyBoxedDraft({
        columnsDef,
        rawColumns: [...columnsDef, "Encounter.mediaAsset2"],
      }),
    ).toBe(true);
    expect(isLegacyBoxedDraft({ columnsDef, rawColumns: columnsDef })).toBe(
      false,
    );
    expect(
      isLegacyBoxedDraft({
        columnsDef: ["Encounter.mediaAsset0"],
        rawColumns: ["Encounter.mediaAsset0", "Encounter.mediaAsset2"],
      }),
    ).toBe(false);
    expect(isLegacyBoxedDraft({})).toBe(false);
  });
});
