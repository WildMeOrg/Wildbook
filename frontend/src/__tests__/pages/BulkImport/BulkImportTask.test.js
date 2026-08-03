import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import BulkImportTask from "../../../pages/BulkImport/BulkImportTask";
import axios from "axios";
import { useSiteSettings } from "../../../SiteSettingsContext";

jest.mock("antd/es/tree-select", () => ({
  __esModule: true,
  default: () => <div>TreeSelect</div>,
}));

jest.mock("../../../models/bulkImport/useGetBulkImportTask", () => jest.fn());
jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));
jest.mock("../../../components/InfoAccordion", () => ({
  __esModule: true,
  default: ({ title, data }) => {
    const renderedTitle =
      typeof title === "string"
        ? title.replace("{count}", data?.[0]?.value ?? "0")
        : title;
    return <div>{renderedTitle}</div>;
  },
}));

jest.mock("../../../components/InfoAccordion", () => ({
  __esModule: true,
  default: ({ title }) => <div>{title}</div>,
}));
jest.mock("../../../components/SimpleDataTable", () => ({
  __esModule: true,
  default: ({ columns = [], data = [] }) => (
    <table data-testid="simple-table">
      <thead>
        <tr>
          {columns.map((col) => (
            <th key={col.name}>{col.name}</th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.map((row, i) => (
          <tr key={i}>
            {columns.map((col) => (
              <td key={col.name}>
                {typeof col.cell === "function"
                  ? col.cell(row)
                  : row[col.selector?.(row)] || "-"}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  ),
}));

const mockUseGetBulkImportTask = require("../../../models/bulkImport/useGetBulkImportTask");

const mockTask = {
  id: "12345",
  sourceName: "upload.xlsx",
  importPercent: 1,
  status: "complete",
  numberMarkedIndividuals: 5,
  iaSummary: {
    numberMediaAssets: 10,
    numberMediaAssetACMIds: 7,
    numberAnnotations: 3,
    numberMediaAssetValidIA: 5,
    detectionPercent: 1,
    detectionStatus: "Complete",
    identificationPercent: 0.5,
    identificationStatus: "In Progress",
  },
  encounters: [
    {
      id: "E123",
      date: "2023-01-01T10:00:00Z",
      submitter: { displayName: "John Doe" },
      occurrenceId: "O456",
      individualId: "I789",
      numberMediaAssets: 2,
      class: "Mammalia",
    },
  ],
};

describe("BulkImportTask", () => {
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

  test("displays spinner during loading", () => {
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: true,
      task: null,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    expect(document.querySelector(".spinner-border")).toBeInTheDocument();
  });

  test("displays task data correctly", () => {
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);

    expect(screen.getByText("BULK_IMPORT_TASK")).toBeInTheDocument();
    expect(
      screen.getAllByText("BULK_IMPORT_TASK_STATUS_complete"),
    ).toHaveLength(2);
    expect(
      screen.getByText("BULK_IMPORT_TASK_STATUS_in_progress"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("simple-table")).toBeInTheDocument();
    expect(screen.getByText("E123")).toBeInTheDocument();
  });

  test("displays modal on error", async () => {
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: null,
      error: { message: "Something went wrong" },
      refetch: jest.fn(),
    });

    renderWithProviders(<BulkImportTask />);
    expect(
      await screen.findByText("BULK_IMPORT_TASK_ERROR"),
    ).toBeInTheDocument();
    expect(screen.getByText("Something went wrong")).toBeInTheDocument();
  });

  test("retry button calls refetch", async () => {
    const refetchMock = jest.fn();
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: null,
      error: { message: "fail" },
      refetch: refetchMock,
    });

    renderWithProviders(<BulkImportTask />);
    fireEvent.click(screen.getByText("RETRY"));
    expect(refetchMock).toHaveBeenCalled();
  });

  test("delete task cancel does not proceed", () => {
    window.confirm = jest.fn(() => false);
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    fireEvent.click(screen.getByText("BULK_IMPORT_DELETE_TASK"));
    expect(window.confirm).toHaveBeenCalled();
  });

  test("delete task success alerts and redirects", async () => {
    window.confirm = jest.fn(() => true);
    window.alert = jest.fn();
    delete window.location;
    window.location = { href: "" };

    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: true,
        status: 200,
        text: () => Promise.resolve("OK"),
      }),
    );

    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    fireEvent.click(screen.getByText("BULK_IMPORT_DELETE_TASK"));

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        "Bulk import task deleted successfully.",
      );
      expect(window.location.href).toBe("/react/");
    });
  });

  test("delete task failure alerts error", async () => {
    window.confirm = jest.fn(() => true);
    window.alert = jest.fn();

    global.fetch = jest.fn(() =>
      Promise.resolve({
        ok: false,
        status: 500,
        text: () => Promise.resolve("Internal Error"),
      }),
    );

    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    fireEvent.click(screen.getByText("BULK_IMPORT_DELETE_TASK"));

    await waitFor(() => {
      expect(window.alert).toHaveBeenCalledWith(
        expect.stringContaining("Failed to delete import task"),
      );
    });
  });

  test("renders table column headers", () => {
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    expect(screen.getByText("Encounter ID")).toBeInTheDocument();
    expect(screen.getByText("Encounter Date")).toBeInTheDocument();
    expect(screen.getByText("Sighting")).toBeInTheDocument();
  });

  test("renders encounter ID as link", () => {
    mockUseGetBulkImportTask.mockReturnValue({
      isLoading: false,
      task: mockTask,
      error: null,
    });

    renderWithProviders(<BulkImportTask />);
    const link = screen.getByText("E123").closest("a");
    expect(link).toHaveAttribute(
      "href",
      "/encounters/encounter.jsp?number=E123",
    );
  });
});

// NOTE: the test intl provider renders message IDS (not translations), so the
// button is matched by its id "BULK_IMPORT_SEND_TO_HOTSPOTTER".
describe("HotSpotter second-pass button", () => {
  // completeTask has one identified (E1) and one unidentified (E2) encounter, and a
  // matchingLocations so the store's initFromPrevious effect enables the button.
  const completeTask = {
    id: "12345",
    status: "complete",
    matchingLocations: ["loc1"],
    iaSummary: {
      detectionStatus: "complete",
      identificationStatus: "complete",
    },
    encounters: [
      { id: "E1", individualId: "MI-1" },
      { id: "E2" }, // no individualId => unidentified
    ],
  };

  const setSite = (hotspotterAvailable) =>
    useSiteSettings.mockReturnValue({
      data: {
        hotspotterAvailable,
        locationData: { locationID: [{ id: "loc1" }] },
      },
      isLoading: false,
      error: null,
    });

  const setRoles = (roles, resendResponse) =>
    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") return Promise.resolve({ data: { roles } });
      return Promise.resolve(
        resendResponse || { status: 200, data: { success: true } },
      );
    });

  beforeEach(() => {
    jest.clearAllMocks();
    delete window.location;
    window.location = {
      search: "?id=12345",
      href: "http://localhost/react/?id=12345",
      reload: jest.fn(),
      assign: jest.fn(),
    };
    mockUseGetBulkImportTask.mockReturnValue({
      task: completeTask,
      isLoading: false,
      error: null,
      refetch: jest.fn(),
    });
  });

  test("is hidden when hotspotterAvailable is false", async () => {
    setSite(false);
    setRoles(["admin"]);
    renderWithProviders(<BulkImportTask />);
    // wait for roles to load, then confirm the button never appears
    await waitFor(() => expect(axios.get).toHaveBeenCalledWith("/api/v3/user"));
    expect(screen.queryByText("BULK_IMPORT_SEND_TO_HOTSPOTTER")).toBeNull();
  });

  test("is hidden for a non-admin even when hotspotter is available", async () => {
    setSite(true);
    setRoles(["researcher"]);
    renderWithProviders(<BulkImportTask />);
    await waitFor(() => expect(axios.get).toHaveBeenCalledWith("/api/v3/user"));
    expect(screen.queryByText("BULK_IMPORT_SEND_TO_HOTSPOTTER")).toBeNull();
  });

  test("admin + available renders the button and it is enabled", async () => {
    setSite(true);
    setRoles(["admin"]);
    renderWithProviders(<BulkImportTask />);
    const btn = await screen.findByText("BULK_IMPORT_SEND_TO_HOTSPOTTER");
    await waitFor(() =>
      expect(btn.closest("button")).not.toBeDisabled(),
    );
  });

  test("sends algorithm=hotspotter&unidentifiedOnly=true on click", async () => {
    setSite(true);
    setRoles(["admin"]);
    window.confirm = jest.fn(() => true);
    window.alert = jest.fn();

    renderWithProviders(<BulkImportTask />);
    const btn = await screen.findByText("BULK_IMPORT_SEND_TO_HOTSPOTTER");
    await waitFor(() => expect(btn.closest("button")).not.toBeDisabled());
    fireEvent.click(btn.closest("button"));

    await waitFor(() => {
      const hit = axios.get.mock.calls
        .map((c) => c[0])
        .find((u) => u.includes("resendBulkImportID.jsp"));
      expect(hit).toBeDefined();
      expect(hit).toContain("algorithm=hotspotter");
      expect(hit).toContain("unidentifiedOnly=true");
    });
  });

  test("treats a string success:'false' response as failure (no reload)", async () => {
    setSite(true);
    setRoles(["admin"], { status: 200, data: { success: "false", error: "nope" } });
    window.confirm = jest.fn(() => true);
    window.alert = jest.fn();

    renderWithProviders(<BulkImportTask />);
    const btn = await screen.findByText("BULK_IMPORT_SEND_TO_HOTSPOTTER");
    await waitFor(() => expect(btn.closest("button")).not.toBeDisabled());
    fireEvent.click(btn.closest("button"));

    await waitFor(() => expect(window.alert).toHaveBeenCalledWith("nope"));
    expect(window.location.reload).not.toHaveBeenCalled();
  });
});
