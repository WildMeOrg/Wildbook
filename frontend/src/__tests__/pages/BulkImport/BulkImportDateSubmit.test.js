import React from "react";
import {
  act,
  fireEvent,
  renderHook,
  screen,
  waitFor,
} from "@testing-library/react";
import { BulkImportSpreadsheet } from "../../../pages/BulkImport/BulkImportSpreadsheet";
import { BulkImportTableReview } from "../../../pages/BulkImport/BulkImportTableReview";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import usePostBulkImport from "../../../models/bulkImport/usePostBulkImport";
import { client } from "../../../api/client";
import { renderWithProviders } from "../../../utils/utils";
import { useSiteSettings } from "../../../SiteSettingsContext";
import { PRODUCTION_SYNONYMS } from "../../../__fixtures__/productionSynonyms";

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
const parse = async (store, csv, rows) => {
  const view = renderWithProviders(<BulkImportSpreadsheet store={store} />);
  fireEvent.change(screen.getByTestId("spreadsheet-input"), {
    target: { files: [new File([csv], "sheet.csv", { type: "text/csv" })] },
  });
  await waitFor(() => expect(store.spreadsheetData.length).toBe(rows), {
    timeout: 5000,
  });
  view.unmount();
};

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
    // the page's site settings, with the production synonym groups
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
    await parse(
      store,
      "Sighting.year,Sighting.month,Sighting.day,Encounter.genus,Encounter.specificEpithet,Encounter.locationID\n" +
        "2021,3,4,Tursiops,truncatus,L1\n" +
        "2022,5,6,Tursiops,truncatus,L1\n",
      2,
    );
    // the table revalidates 500 ms after site settings arrive; run that timer
    jest.useFakeTimers();
    try {
      store.setValidationErrors({ stale: { x: "y" } });
      renderWithProviders(<BulkImportTableReview store={store} />);
      expect(store.synonymFields).toEqual(PRODUCTION_SYNONYMS);
      act(() => jest.advanceTimersByTime(500));
    } finally {
      jest.useRealTimers();
    }
    expect(store.columnsDef).toContain("Encounter.year");
    expect(store.columnsDef).not.toContain("Sighting.year");
    expect(store.missingRequiredColumns).toEqual([]);
    expect(store.validationErrors).toEqual({});
    const next = document.getElementById("table-review-next-button");
    expect(next).not.toBeNull();
    expect(next).toBeEnabled();
  });
});
