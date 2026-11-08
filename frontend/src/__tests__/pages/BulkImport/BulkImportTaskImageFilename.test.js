import React from "react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import BulkImportTask from "../../../pages/BulkImport/BulkImportTask";
import axios from "axios";
import useGetBulkImportTask from "../../../models/bulkImport/useGetBulkImportTask";
import { useSiteSettings } from "../../../SiteSettingsContext";

// Issue #1795: the bulk import task table shows the original image file
// name(s) of each encounter, so researchers need not open every encounter.
//
// Mocks follow BulkImportTaskClassCell.test.js (require()-based factories,
// which hoist cleanly under babel-plugin-jest-hoist 27).

jest.mock("axios");
jest.mock("../../../models/bulkImport/useGetBulkImportTask");
jest.mock("../../../SiteSettingsContext");
jest.mock("antd/es/tree-select", () => {
  const React = require("react");
  function TreeSelect() {
    return React.createElement("div", null, "TreeSelect");
  }
  return { __esModule: true, default: TreeSelect };
});
jest.mock("../../../components/InfoAccordion", () => {
  const React = require("react");
  function InfoAccordion({ title }) {
    return React.createElement("div", null, title);
  }
  return { __esModule: true, default: InfoAccordion };
});
jest.mock("../../../components/SimpleDataTable", () => {
  const React = require("react");
  function SimpleDataTable({ columns = [], data = [] }) {
    return React.createElement(
      "table",
      { "data-testid": "simple-table" },
      React.createElement(
        "thead",
        null,
        React.createElement(
          "tr",
          null,
          columns.map((col) =>
            React.createElement("th", { key: col.name }, col.name),
          ),
        ),
      ),
      React.createElement(
        "tbody",
        null,
        data.map((row, i) =>
          React.createElement(
            "tr",
            { key: i },
            columns.map((col) =>
              React.createElement(
                "td",
                { key: col.name, "data-column": col.name },
                typeof col.cell === "function" ? col.cell(row) : null,
              ),
            ),
          ),
        ),
      ),
    );
  }
  return { __esModule: true, default: SimpleDataTable };
});

const COLUMN = "Image File Name";

const renderWithEncounter = (encounter) => {
  useGetBulkImportTask.mockReturnValue({
    isLoading: false,
    error: null,
    refetch: jest.fn(),
    task: {
      id: "12345",
      sourceName: "upload.xlsx",
      importPercent: 1,
      status: "complete",
      numberMarkedIndividuals: 0,
      iaSummary: {
        numberMediaAssets: 1,
        numberAnnotations: 1,
        detectionPercent: 1,
        detectionStatus: "Complete",
        identificationPercent: 1,
        identificationStatus: "Complete",
      },
      encounters: [
        {
          id: "E123",
          date: "2023-01-01T10:00:00Z",
          submitter: { displayName: "John Doe" },
          numberMediaAssets: 2,
          ...encounter,
        },
      ],
    },
  });
  renderWithProviders(<BulkImportTask />);
};

const filenameCell = () =>
  document.querySelector(`td[data-column="${COLUMN}"]`);

describe("BulkImportTask — image file name column (issue #1795)", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useSiteSettings.mockReturnValue({
      data: {},
      isLoading: false,
      error: null,
    });
    delete window.location;
    window.location = new URL("http://localhost/react/?id=12345");
    axios.get.mockResolvedValue({ data: { roles: [] } });
  });

  test("adds the column header", () => {
    renderWithEncounter({ mediaAssetFilenames: ["IMG_0001.JPG"] });
    expect(screen.getByRole("columnheader", { name: COLUMN })).toBeTruthy();
  });

  test("lists every file name of the encounter, in order", () => {
    renderWithEncounter({
      mediaAssetFilenames: ["IMG_0001.JPG", "fluke left é.png"],
    });
    const names = Array.from(filenameCell().querySelectorAll("span")).map(
      (s) => s.textContent,
    );
    expect(names).toEqual(["IMG_0001.JPG", "fluke left é.png"]);
  });

  test("shows a dash when the server sends no file names", () => {
    renderWithEncounter({});
    expect(filenameCell()).toHaveTextContent(/^-$/);
  });

  test("renders a file name as text, never as markup", () => {
    renderWithEncounter({ mediaAssetFilenames: ["<b>x</b>.jpg"] });
    expect(filenameCell().querySelector("b")).toBeNull();
    expect(filenameCell()).toHaveTextContent("<b>x</b>.jpg");
  });
});
