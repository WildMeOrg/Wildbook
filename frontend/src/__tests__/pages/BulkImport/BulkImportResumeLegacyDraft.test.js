import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import BulkImportContinueModal from "../../../pages/BulkImport/BulkImportContinueModal";
import { BulkImportSpreadsheet } from "../../../pages/BulkImport/BulkImportSpreadsheet";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import ThemeContext from "../../../ThemeColorProvider";
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
const A2 = "Encounter.mediaAsset2";
const theme = {
  primaryColors: { primary50: "#fff", primary100: "#fff", primary500: "#000" },
  wildMeColors: { cyan700: "#0ff" },
  defaultColors: { white: "#fff" },
};
const base = {
  submissionId: "abc-123",
  spreadsheetFileName: "old.csv",
  spreadsheetUploadProgress: 100,
  uploadedImages: ["a.jpg", "c.jpg"],
  columnsDef: [A0, `${A0}.bbox`],
  rawColumns: [A0, A2, `${A0}.bbox`],
  spreadsheetData: [{ [A0]: "a.jpg, c.jpg", [`${A0}.bbox`]: "1,1,5,5" }],
  rawData: [{ [A0]: "a.jpg", [A2]: "c.jpg", [`${A0}.bbox`]: "1,1,5,5" }],
  lastSavedAt: 1620000000000,
};

afterEach(() => localStorage.clear());

[
  ["untouched", base],
  [
    "edited",
    { ...base, rawData: [{ ...base.rawData[0], [A0]: "a.jpg, c.jpg" }] },
  ],
].forEach(([name, draft]) => {
  test(`${name} legacy boxed draft: images kept, spreadsheet must be uploaded again`, () => {
    localStorage.setItem("BulkImportStore", JSON.stringify(draft));
    const store = new BulkImportStore();
    store.fetchAndApplyUploaded = jest.fn();
    store.setIndexableFields(["Encounter.mediaAsset#.bbox"]);
    render(
      <ThemeContext.Provider value={theme}>
        <BulkImportContinueModal store={store} setRenderMode1={jest.fn()} />
      </ThemeContext.Provider>,
    );
    fireEvent.click(screen.getByText("CONTINUE"));

    expect(store.submissionId).toBe("abc-123");
    expect(store.uploadedImages).toEqual(["a.jpg", "c.jpg"]);
    expect(store.spreadsheetData).toEqual([]);
    expect(store.rawData).toEqual([]);
    expect(store.rawColumns).toEqual([]);
    expect(store.columnsDef).toEqual([]);
    expect(store.spreadsheetUploadProgress).toBe(0);
    expect(store.spreadsheetFileName).toBe("");

    renderWithProviders(
      <ThemeContext.Provider value={theme}>
        <BulkImportSpreadsheet store={store} />
      </ThemeContext.Provider>,
    );
    expect(screen.getByTestId("spreadsheet-input")).toBeInTheDocument();
    expect(document.getElementById("next-button")).toBeDisabled();
  });
});

test("resume waits for site settings, so a current boxed draft keeps its slots", () => {
  const current = {
    ...base,
    columnsDef: [A0, `${A0}.bbox`, A2],
    spreadsheetData: [
      { [A0]: "a.jpg", [`${A0}.bbox`]: "1,1,5,5", [A2]: "c.jpg" },
    ],
  };
  localStorage.setItem("BulkImportStore", JSON.stringify(current));
  const store = new BulkImportStore();
  store.fetchAndApplyUploaded = jest.fn();
  render(
    <ThemeContext.Provider value={theme}>
      <BulkImportContinueModal store={store} setRenderMode1={jest.fn()} />
    </ThemeContext.Provider>,
  );
  const resume = screen.getByText("CONTINUE").closest("button");
  expect(resume).toBeDisabled();
  act(() => store.setIndexableFields(["Encounter.mediaAsset#.bbox"]));
  expect(resume).not.toBeDisabled();
  fireEvent.click(resume);
  expect(store.isBoxedSheet).toBe(true);
  expect(store.photoColumns).toEqual([A0, A2]);
  expect(store.spreadsheetData[0][A2]).toBe("c.jpg");
});
