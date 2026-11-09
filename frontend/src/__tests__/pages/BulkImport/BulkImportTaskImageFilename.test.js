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

// setupTests.js replaces useIntl().formatMessage with one that never
// interpolates values; use the real react-intl here so "+N more" is checked.
jest.mock("react-intl", () => jest.requireActual("react-intl"));
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

  test("lists several file names as separate list items, in order", () => {
    renderWithEncounter({
      mediaAssetFilenames: [
        "45528e1d-8d65-4f92-af0b-29f8ee603cdd-master.jpg",
        "003A9525.jpg",
      ],
    });
    const names = Array.from(filenameCell().querySelectorAll("li")).map(
      (li) => li.textContent,
    );
    expect(names).toEqual([
      "45528e1d-8d65-4f92-af0b-29f8ee603cdd-master.jpg",
      "003A9525.jpg",
    ]);
  });

  test("shows up to three names with nothing collapsed", () => {
    renderWithEncounter({ mediaAssetFilenames: ["a.jpg", "b.jpg", "c.jpg"] });
    expect(filenameCell().querySelectorAll("li")).toHaveLength(3);
    expect(filenameCell().querySelector("details")).toBeNull();
  });

  test("collapses a fourth name behind '+1 more'", () => {
    renderWithEncounter({
      mediaAssetFilenames: ["a.jpg", "b.jpg", "c.jpg", "d.jpg"],
    });
    const details = filenameCell().querySelector("details");
    expect(details.querySelector("summary")).toHaveTextContent(/^\+1 more$/);
    expect(
      Array.from(details.querySelectorAll("li")).map((li) => li.textContent),
    ).toEqual(["d.jpg"]);
  });

  test("collapses names past the first three behind '+N more'", () => {
    const names = Array.from({ length: 150 }, (_, i) => `IMG_${i}.jpg`);
    renderWithEncounter({ mediaAssetFilenames: names });

    const details = filenameCell().querySelector("details");
    expect(details).not.toBeNull();
    expect(details.open).toBe(false);
    expect(details.querySelector("summary")).toHaveTextContent(/^\+147 more$/);

    // the first three are outside the collapsed block, the rest inside it
    const visible = Array.from(
      filenameCell().querySelectorAll(":scope > div > ul > li"),
    ).map((li) => li.textContent);
    expect(visible).toEqual(["IMG_0.jpg", "IMG_1.jpg", "IMG_2.jpg"]);
    const collapsed = Array.from(details.querySelectorAll("li")).map(
      (li) => li.textContent,
    );
    expect(collapsed).toHaveLength(147);
    expect(collapsed[0]).toBe("IMG_3.jpg");
    expect(collapsed[146]).toBe("IMG_149.jpg");
  });

  test("shows a single file name without a list", () => {
    renderWithEncounter({ mediaAssetFilenames: ["IMG_0001.JPG"] });
    expect(filenameCell().querySelector("li")).toBeNull();
    expect(filenameCell()).toHaveTextContent(/^IMG_0001\.JPG$/);
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
