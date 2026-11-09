import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import BulkImportTask from "../../../pages/BulkImport/BulkImportTask";
import axios from "axios";
import useGetBulkImportTask from "../../../models/bulkImport/useGetBulkImportTask";
import { useSiteSettings } from "../../../SiteSettingsContext";

// Search box on the bulk import task table.
//
// Mocks follow BulkImportTaskClassCell.test.js (require()-based factories,
// which hoist cleanly under babel-plugin-jest-hoist 27). setupTests.js
// replaces useIntl().formatMessage with one that never interpolates values;
// use the real react-intl so the "{shown} of {total}" line is checked.

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
// renders one <tr data-encounter="..."> per row, ignoring paging
jest.mock("../../../components/SimpleDataTable", () => {
  const React = require("react");
  function SimpleDataTable({ data = [] }) {
    return React.createElement(
      "table",
      null,
      React.createElement(
        "tbody",
        null,
        data.map((row) =>
          React.createElement(
            "tr",
            { key: row.encounterID, "data-encounter": row.encounterID },
            React.createElement("td", null, row.tableID),
          ),
        ),
      ),
    );
  }
  return { __esModule: true, default: SimpleDataTable };
});

const encounter = (id, createdMillis, extra = {}) => ({
  id,
  date: "2025-10-25",
  createdMillis,
  submitter: { displayName: "Jane Researcher" },
  numberMediaAssets: 1,
  mediaAssetFilenames: [],
  ...extra,
});

const renderTask = () => {
  useGetBulkImportTask.mockReturnValue({
    isLoading: false,
    error: null,
    refetch: jest.fn(),
    task: {
      id: "12345",
      sourceName: "upload.xlsx",
      importPercent: 1,
      status: "complete",
      numberMarkedIndividuals: 1,
      iaSummary: {
        numberMediaAssets: 6,
        numberAnnotations: 3,
        detectionPercent: 1,
        detectionStatus: "Complete",
        identificationPercent: 1,
        identificationStatus: "Complete",
        statsAnnotations: {
          encounterTaskInfo: {
            "enc-c": [["task-1", "completed", "giraffe_whole"]],
            // malformed tuple: the Class cell shows "-", so search must not match it
            "enc-a": [["task-x", "orphaned_status"]],
          },
        },
      },
      encounters: [
        encounter("enc-a", 1, {
          occurrenceId: "sighting-77",
          mediaAssetFilenames: ["IMG_0001.JPG"],
        }),
        encounter("enc-b", 2, {
          individualId: "indiv-uuid-9",
          individualDisplayName: "Twiga",
          // the 5th name sits under "+2 more" in the real cell
          mediaAssetFilenames: [
            "a.jpg",
            "b.jpg",
            "c.jpg",
            "d.jpg",
            "003A9525.jpg",
          ],
        }),
        encounter("enc-c", 3, { mediaAssetFilenames: ["IMG_0002.JPG"] }),
      ],
    },
  });
  renderWithProviders(<BulkImportTask />);
};

const shownEncounters = () =>
  Array.from(document.querySelectorAll("tr[data-encounter]")).map((tr) => [
    tr.getAttribute("data-encounter"),
    tr.textContent,
  ]);

const search = (text) =>
  fireEvent.change(
    screen.getByRole("searchbox", { name: "Search Encounters" }),
    { target: { value: text } },
  );

describe("BulkImportTask — search box", () => {
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

  test("shows every encounter and no match count when empty", () => {
    renderTask();
    expect(shownEncounters()).toEqual([
      ["enc-a", "1"],
      ["enc-b", "2"],
      ["enc-c", "3"],
    ]);
    expect(screen.queryByText(/Matching encounters/)).toBeNull();
  });

  test("finds a file name collapsed under '+N more', keeping its # number", () => {
    renderTask();
    search("003a9525");
    expect(shownEncounters()).toEqual([["enc-b", "2"]]);
    expect(screen.getByText("Matching encounters: 1 / 3")).toBeTruthy();
  });

  test("matches encounter, sighting, individual and class text, ignoring case and spaces", () => {
    renderTask();
    search("  ENC-A ");
    expect(shownEncounters().map(([id]) => id)).toEqual(["enc-a"]);
    search("sighting-77");
    expect(shownEncounters().map(([id]) => id)).toEqual(["enc-a"]);
    search("twiga");
    expect(shownEncounters().map(([id]) => id)).toEqual(["enc-b"]);
    search("indiv-uuid-9");
    expect(shownEncounters().map(([id]) => id)).toEqual(["enc-b"]);
    search("giraffe_whole");
    expect(shownEncounters().map(([id]) => id)).toEqual(["enc-c"]);
  });

  test("treats whitespace-only input as no search", () => {
    renderTask();
    search("   ");
    expect(shownEncounters()).toHaveLength(3);
    expect(screen.queryByText(/Matching encounters/)).toBeNull();
  });

  test("ignores malformed class entries the Class cell doesn't show", () => {
    renderTask();
    search("orphaned_status");
    expect(shownEncounters()).toEqual([]);
  });

  test("matches the date and the user shown in the row", () => {
    renderTask();
    search("2025-10-25");
    expect(shownEncounters()).toHaveLength(3);
    search("jane researcher");
    expect(shownEncounters()).toHaveLength(3);
  });

  test("shows no rows and a zero count when nothing matches; clearing restores all", () => {
    renderTask();
    search("no-such-thing");
    expect(shownEncounters()).toEqual([]);
    expect(screen.getByText("Matching encounters: 0 / 3")).toBeTruthy();
    search("");
    expect(shownEncounters()).toHaveLength(3);
  });
});
