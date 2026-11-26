import React from "react";
import { act, renderHook } from "@testing-library/react";
import { BulkImportTableReview } from "../../../pages/BulkImport/BulkImportTableReview";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import usePostBulkImport from "../../../models/bulkImport/usePostBulkImport";
import { client } from "../../../api/client";
import { useSiteSettings } from "../../../SiteSettingsContext";
import { PRODUCTION_SYNONYMS } from "../../../__fixtures__/productionSynonyms";
import { renderWithProviders } from "../../../utils/utils";
import { uploadCsv } from "../../../__fixtures__/bulkImportUpload";

jest.mock("@flowjs/flow.js", () =>
  jest.fn().mockImplementation(() => ({
    opts: { query: {} },
    assignBrowse: jest.fn(),
    on: jest.fn(),
    files: [],
  })),
);

jest.mock("../../../api/client", () => ({ client: { post: jest.fn() } }));
jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));

const A0 = "Encounter.mediaAsset0";

const DATE_PART_RE = /\.(year|month|day|hour|minutes|seconds)$/;

const newStore = (setup) => {
  const store = new BulkImportStore();
  store.setIndexableFields(["Encounter.mediaAsset#"]);
  store.setSynonymFields(PRODUCTION_SYNONYMS);
  if (setup) setup(store);
  return store;
};

// the real parser, as on the page
const parse = (store, csv, rows) =>
  uploadCsv(csv, rows, store, { unmount: true });

// what handleStartImport does: write back, then post the submittable columns
const submit = async (store) => {
  client.post.mockResolvedValue({ status: 200 });
  store.updateRawFromNormalizedRow();
  const { result } = renderHook(() => usePostBulkImport());
  await act(() =>
    result.current.submit(
      "id",
      store.submittableColumns,
      store.rawData,
      "sheet.csv",
      [],
      false,
      false,
    ),
  );
  const payload = client.post.mock.calls[0][1];
  const at = (r, f) => payload.rows[r][payload.fieldNames.indexOf(f)];
  return { payload, at };
};

const count = (list, x) => list.filter((v) => v === x).length;

// a parsed sheet on the review step, with the page's site settings (production
// synonym groups); the table revalidates 500 ms after settings arrive
const reviewSheet = async (csv, rows) => {
  useSiteSettings.mockReturnValue({
    data: {
      bulkImportMinimalFields: {},
      locationData: { locationID: [{ id: "L1" }] },
      users: [],
      siteTaxonomies: [{ scientificName: "Tursiops truncatus" }],
      bulkImportFieldNameSynonyms: PRODUCTION_SYNONYMS,
    },
  });
  const store = newStore((s) => {
    s.setValidSpecies(["Tursiops truncatus"]);
    s.setValidLocationIDs(["L1"]);
  });
  await parse(store, csv, rows);
  jest.useFakeTimers();
  try {
    store.setValidationErrors({ stale: { x: "y" } });
    renderWithProviders(<BulkImportTableReview store={store} />);
    expect(store.synonymFields).toEqual(PRODUCTION_SYNONYMS);
    act(() => jest.advanceTimersByTime(500));
  } finally {
    jest.useRealTimers();
  }
  return store;
};

const nextButton = () => {
  const next = document.getElementById("table-review-next-button");
  expect(next).not.toBeNull();
  return next;
};

describe("bulk-import dates on submit", () => {
  beforeEach(() => {
    client.post.mockReset();
  });

  test("two Sighting-date rows keep their source columns once", async () => {
    const store = newStore();
    await parse(
      store,
      `Sighting.year,Sighting.month,Sighting.day,Encounter.genus,Encounter.specificEpithet,${A0}\n` +
        "2021,3,4,Tursiops,truncatus,a.jpg\n" +
        "2022,5,6,Tursiops,truncatus,b.jpg\n",
      2,
    );
    const { payload, at } = await submit(store);
    ["Sighting.year", "Sighting.month", "Sighting.day"].forEach((f) =>
      expect(count(payload.fieldNames, f)).toBe(1),
    );
    expect(payload.fieldNames).not.toContain("Encounter.year");
    expect(payload.fieldNames).not.toContain("Sighting.hour");
    expect(payload.fieldNames).not.toContain("Sighting.minutes");
    expect(payload.fieldNames).not.toContain("Sighting.seconds");
    const date = (r) =>
      ["Sighting.year", "Sighting.month", "Sighting.day"].map((f) => at(r, f));
    expect(date(0)).toEqual([2021, 3, 4]);
    expect(date(1)).toEqual([2022, 5, 6]);
    expect(at(0, A0)).toBe("a.jpg");
    expect(at(1, "Encounter.genus")).toBe("Tursiops");
    expect(at(1, "Encounter.specificEpithet")).toBe("truncatus");
  });

  test("millis-only sheet posts no calendar columns", async () => {
    const store = newStore();
    await parse(
      store,
      `Encounter.dateInMilliseconds,Encounter.genus,${A0}\n` +
        "1700000000000,Tursiops,a.jpg\n",
      1,
    );
    const { payload, at } = await submit(store);
    expect(payload.fieldNames.filter((f) => DATE_PART_RE.test(f))).toEqual([]);
    expect(at(0, "Encounter.dateInMilliseconds")).toBe(1700000000000);
  });

  test("labeled photo keyword named seconds is posted", async () => {
    const store = newStore((s) => {
      s.setLabeledKeywordAllowedKeys(["seconds"]);
      s.setLabeledKeywordAllowedPairs({ seconds: ["fast"] });
    });
    await parse(
      store,
      `Encounter.year,Encounter.genus,${A0},${A0}.seconds\n` +
        "2020,Tursiops,a.jpg,fast\n",
      1,
    );
    const { payload, at } = await submit(store);
    expect(payload.fieldNames).toContain(`${A0}.seconds`);
    expect(at(0, `${A0}.seconds`)).toBe("fast");
    expect(payload.fieldNames).not.toContain("Encounter.seconds");
  });

  test("uploaded seconds header is not posted", async () => {
    const store = newStore();
    await parse(
      store,
      `Encounter.year,Encounter.month,Encounter.seconds,Encounter.genus,${A0}\n` +
        "2020,6,5,Tursiops,a.jpg\n",
      1,
    );
    expect(store.rawColumns).toContain("Encounter.seconds");
    const { payload, at } = await submit(store);
    expect(payload.fieldNames).not.toContain("Encounter.seconds");
    expect(payload.fieldNames).toContain("Encounter.year");
    expect(at(0, "Encounter.year")).toBe(2020);
    expect(at(0, "Encounter.month")).toBe(6);
  });

  test("sightingDateSheetNextEnabled", async () => {
    const store = await reviewSheet(
      "Sighting.year,Sighting.month,Sighting.day,Encounter.genus,Encounter.specificEpithet,Encounter.locationID\n" +
        "2021,3,4,Tursiops,truncatus,L1\n" +
        "2022,5,6,Tursiops,truncatus,L1\n",
      2,
    );
    // the source date stays in the display row and the headers (the old parser
    // deleted Sighting.year from both, which also avoided the synonym error)
    expect(store.spreadsheetData[0]["Sighting.year"]).toBe(2021);
    expect(store.spreadsheetData[0]["Encounter.year"]).toBe("2021-03-04");
    expect(store.rawColumns).toContain("Sighting.year");
    expect(store.dateDisplaySource).toBe("Sighting");
    expect(store.columnsDef).toContain("Encounter.year");
    expect(store.columnsDef).not.toContain("Sighting.year");
    expect(store.missingRequiredColumns).toEqual([]);
    expect(store.validationErrors).toEqual({});
    expect(nextButton()).toBeEnabled();
  });

  test("a millis-only row with a blank millis cell disables Next", async () => {
    const store = await reviewSheet(
      "Encounter.dateInMilliseconds,Encounter.genus,Encounter.specificEpithet,Encounter.locationID\n" +
        "1700000000000,Tursiops,truncatus,L1\n" +
        ",Tursiops,truncatus,L1\n",
      2,
    );
    expect(store.missingRequiredColumns).toEqual([]);
    expect(store.validationErrors[0]).toBeUndefined();
    expect(store.validationErrors[1]["Encounter.dateInMilliseconds"]).toBe(
      "BULKIMPORT_ERROR_INVALID_REQUIREDFIELD",
    );
    expect(nextButton()).toBeDisabled();
  });

  test("an edited Sighting date with a one-digit hour posts that hour", async () => {
    const store = newStore();
    await parse(
      store,
      `Sighting.year,Sighting.month,Sighting.day,Encounter.genus,${A0}\n` +
        "2020,1,2,Tursiops,a.jpg\n",
      1,
    );
    store.updateCellValue(0, "Encounter.year", "2021-03-04T9");
    // the date cell accepts it...
    expect(store.validateRow(0).errors).not.toHaveProperty("Encounter.year");
    const { payload, at } = await submit(store);
    // ...and write-back reads the same parts
    expect(store.rawData[0]).toMatchObject({
      "Sighting.year": 2021,
      "Sighting.month": 3,
      "Sighting.day": 4,
      "Sighting.hour": 9,
    });
    expect(at(0, "Sighting.year")).toBe(2021);
    expect(at(0, "Sighting.month")).toBe(3);
    expect(at(0, "Sighting.day")).toBe(4);
    expect(at(0, "Sighting.hour")).toBe(9);
    expect(payload.fieldNames).not.toContain("Sighting.minutes");
    expect(payload.fieldNames).not.toContain("Encounter.year");
  });
});
