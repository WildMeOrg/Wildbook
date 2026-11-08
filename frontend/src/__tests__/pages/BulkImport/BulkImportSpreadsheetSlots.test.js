import React from "react";
import { act, fireEvent, screen, waitFor } from "@testing-library/react";
import { BulkImportSpreadsheet } from "../../../pages/BulkImport/BulkImportSpreadsheet";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import { renderWithProviders } from "../../../utils/utils";

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

  test("an un-boxed sheet still merges photos into slot 0", async () => {
    const store = await upload(`${A0},${A1}\na.jpg,b.jpg\n`);
    expect(store.columnsDef).toContain(A0);
    expect(store.columnsDef).not.toContain(A1);
    expect(store.spreadsheetData[0][A0]).toBe("a.jpg, b.jpg");
  });
});
