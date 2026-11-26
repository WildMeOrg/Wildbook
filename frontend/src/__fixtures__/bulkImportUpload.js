/* global expect */
import React from "react";
import { fireEvent, screen, waitFor } from "@testing-library/react";
import { BulkImportSpreadsheet } from "../pages/BulkImport/BulkImportSpreadsheet";
import BulkImportStore from "../pages/BulkImport/BulkImportStore";
import { renderWithProviders } from "../utils/utils";

// a store whose site settings allow photo slots and boxes
export const boxStore = () => {
  const store = new BulkImportStore();
  store.setIndexableFields([
    "Encounter.mediaAsset#",
    "Encounter.mediaAsset#.bbox",
  ]);
  return store;
};

// parses a CSV through the real spreadsheet step and waits for the parse to finish
// (progress 100) with that many rows; unmount: true removes the spreadsheet step after
export const uploadCsv = async (
  csv,
  rows = 1,
  store = boxStore(),
  { unmount = false } = {},
) => {
  const view = renderWithProviders(<BulkImportSpreadsheet store={store} />);
  fireEvent.change(screen.getByTestId("spreadsheet-input"), {
    target: { files: [new File([csv], "sheet.csv", { type: "text/csv" })] },
  });
  await waitFor(
    () => {
      expect(store.spreadsheetUploadProgress).toBe(100);
      expect(store.spreadsheetData.length).toBe(rows);
    },
    { timeout: 5000 },
  );
  if (unmount) view.unmount();
  return store;
};
