import React from "react";
import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { BulkImportSpreadsheet } from "../../../pages/BulkImport/BulkImportSpreadsheet";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import { renderWithProviders } from "../../../utils/utils";
import * as XLSX from "xlsx";

jest.mock("@flowjs/flow.js", () =>
  jest.fn().mockImplementation(() => ({
    opts: { query: {} },
    assignBrowse: jest.fn(),
    on: jest.fn(),
    files: [],
  })),
);

const A0 = "Encounter.mediaAsset0";
const A1 = "Encounter.mediaAsset1";
const A2 = "Encounter.mediaAsset2";

const upload = async (csv) => {
  const store = new BulkImportStore();
  store.setIndexableFields([
    "Encounter.mediaAsset#",
    "Encounter.mediaAsset#.bbox",
  ]);
  renderWithProviders(<BulkImportSpreadsheet store={store} />);
  fireEvent.change(screen.getByTestId("spreadsheet-input"), {
    target: { files: [new File([csv], "sheet.csv", { type: "text/csv" })] },
  });
  await waitFor(() => expect(store.spreadsheetData.length).toBe(1));
  return store;
};

describe("BulkImportSpreadsheet photo slots", () => {
  test("a sheet dropped before site settings load is ignored, then parses with slots", async () => {
    const store = new BulkImportStore();
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    const input = screen.getByTestId("spreadsheet-input");
    const csv = `${A0},${A2},${A0}.bbox\na.jpg,c.jpg,"1,2,3,4"\n`;
    const file = () => new File([csv], "sheet.csv", { type: "text/csv" });
    expect(screen.getByRole("button", { name: /browse/i })).toBeDisabled();
    fireEvent.change(input, { target: { files: [file()] } });
    await new Promise((r) => setTimeout(r, 50));
    expect(store.spreadsheetData.length).toBe(0);
    act(() =>
      store.setIndexableFields([
        "Encounter.mediaAsset#",
        "Encounter.mediaAsset#.bbox",
      ]),
    );
    fireEvent.change(input, { target: { files: [file()] } });
    await waitFor(() => expect(store.spreadsheetData.length).toBe(1));
    expect(store.columnsDef).toEqual(expect.arrayContaining([A0, A2]));
    expect(store.spreadsheetData[0][A2]).toBe("c.jpg");
  });

  test("a boxed sheet keeps numbered photo columns unmerged", async () => {
    const store = await upload(
      `${A0},${A2},${A0}.bbox\na.jpg,c.jpg,"1,2,3,4"\n`,
    );
    expect(store.columnsDef).toEqual(expect.arrayContaining([A0, A2]));
    expect(store.spreadsheetData[0][A0]).toBe("a.jpg");
    expect(store.spreadsheetData[0][A2]).toBe("c.jpg");
  });

  test("a box with three-digit groups stays text, not a thousands number", async () => {
    const store = await upload(
      `${A0},${A0}.bbox,${A0}.theta\na.jpg,"362,285,296,342",0.3\n`,
    );
    expect(store.spreadsheetData[0][`${A0}.bbox`]).toBe("362,285,296,342");
    expect(store.validationErrors[0]?.[`${A0}.bbox`]).toBeUndefined();
  });

  test("box text survives across rows with blanks; theta stays a number", async () => {
    const store = new BulkImportStore();
    store.setIndexableFields([
      "Encounter.mediaAsset#",
      "Encounter.mediaAsset#.bbox",
    ]);
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    const csv =
      `${A0},${A0}.bbox,${A0}.theta,${A0}.bboxSource\n` +
      `a.jpg,"362,285,296,342",0.3,00123\n` +
      `,,,\n` +
      `b.jpg,,,\n` +
      `c.jpg,"40,34,514,695",,\n`;
    fireEvent.change(screen.getByTestId("spreadsheet-input"), {
      target: { files: [new File([csv], "sheet.csv", { type: "text/csv" })] },
    });
    await waitFor(() => expect(store.spreadsheetData.length).toBe(3));
    const rows = store.spreadsheetData;
    expect(rows.map((r) => r[`${A0}.bbox`])).toEqual([
      "362,285,296,342",
      "",
      "40,34,514,695",
    ]);
    expect(rows[0][`${A0}.theta`]).toBe(0.3);
    expect(rows[0][`${A0}.bboxSource`]).toBe("00123");
  });

  test("an Excel box typed as a comma-formatted number keeps its text", async () => {
    const ws = XLSX.utils.aoa_to_sheet([
      [A0, `${A0}.bbox`],
      ["a.jpg", 362285296342],
      ["b.jpg", 362285296342],
    ]);
    ws.B2.z = "#,##0";
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "S");
    const buf = XLSX.write(wb, { type: "array", bookType: "xlsx" });
    const store = new BulkImportStore();
    store.setIndexableFields([
      "Encounter.mediaAsset#",
      "Encounter.mediaAsset#.bbox",
    ]);
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    fireEvent.change(screen.getByTestId("spreadsheet-input"), {
      target: { files: [new File([buf], "sheet.xlsx")] },
    });
    await waitFor(() => expect(store.spreadsheetData.length).toBe(2));
    expect(store.spreadsheetData[0][`${A0}.bbox`]).toBe("362,285,296,342");
    // a General-format number has no commas to recover and stays invalid
    expect(store.spreadsheetData[1][`${A0}.bbox`]).not.toMatch(/,/);
    expect(store.validationErrors[1]?.[`${A0}.bbox`]).toBeTruthy();
  });

  test("an un-boxed sheet still merges photos into slot 0", async () => {
    const store = await upload(`${A0},${A1}\na.jpg,b.jpg\n`);
    expect(store.columnsDef).toContain(A0);
    expect(store.columnsDef).not.toContain(A1);
    expect(store.spreadsheetData[0][A0]).toBe("a.jpg, b.jpg");
  });
});
