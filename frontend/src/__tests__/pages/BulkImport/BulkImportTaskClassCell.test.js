import React from "react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import BulkImportTask from "../../../pages/BulkImport/BulkImportTask";
import axios from "axios";
import useGetBulkImportTask from "../../../models/bulkImport/useGetBulkImportTask";
import { useSiteSettings } from "../../../SiteSettingsContext";

// Issue #1744: ImportTask.statsAnnotations now reports one match task per
// matched annotation of an encounter (an encounter can hold several, e.g.
// body + part). The Class cell must expose every one of them, not just the
// first tuple.
//
// This lives in its own file (rather than BulkImportTask.test.js) because
// that suite's jest.mock factories crash babel-plugin-jest-hoist 27 under the
// newer @babel/types in node_modules; the mocks below use automocks and
// require()-based factories, which hoist cleanly.

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
        "tbody",
        null,
        data.map((row, i) =>
          React.createElement(
            "tr",
            { key: i },
            columns.map((col) =>
              React.createElement(
                "td",
                { key: col.name },
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

const baseTask = {
  id: "12345",
  sourceName: "upload.xlsx",
  importPercent: 1,
  status: "complete",
  numberMarkedIndividuals: 0,
  iaSummary: {
    numberMediaAssets: 1,
    numberAnnotations: 2,
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
      numberMediaAssets: 1,
    },
  ],
};

const renderWithTaskInfo = (encounterTaskInfo) => {
  useGetBulkImportTask.mockReturnValue({
    isLoading: false,
    error: null,
    refetch: jest.fn(),
    task: {
      ...baseTask,
      iaSummary: {
        ...baseTask.iaSummary,
        statsAnnotations: { encounterTaskInfo },
      },
    },
  });
  renderWithProviders(<BulkImportTask />);
};

describe("BulkImportTask — Class cell match-results links (issue #1744)", () => {
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

  test("renders one link per annotation task, newest first as reported", () => {
    renderWithTaskInfo({
      E123: [
        ["task-body", "completed", "dolphin_body"],
        ["task-fin", "completed", "dolphin_fin"],
      ],
    });

    const links = screen.getAllByRole("link", { name: /dolphin_/ });
    expect(links.map((a) => a.getAttribute("href"))).toEqual([
      "/react/match-results?taskId=task-body",
      "/react/match-results?taskId=task-fin",
    ]);
    expect(links[0]).toHaveTextContent("dolphin_body : completed");
  });

  test("renders a dash when the encounter has no match task", () => {
    renderWithTaskInfo({});
    expect(
      document.querySelector('a[href^="/react/match-results?taskId="]'),
    ).toBeNull();
    // the Class cell falls back to a dash (other cells may show one too)
    expect(screen.getAllByText("-").length).toBeGreaterThan(0);
  });
});
