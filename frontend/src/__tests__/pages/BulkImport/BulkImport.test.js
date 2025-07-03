// __tests__/BulkImport.test.jsx
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import BulkImport from "../../../pages/BulkImport/BulkImport";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import useGetBulkImportTask from "../../../models/bulkImport/useGetBulkImportTask";

// Named‐export mocks
jest.mock("../../../pages/BulkImport/BulkImportImageUpload", () => ({
  BulkImportImageUpload: () => <div data-testid="image-upload" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportUploadProgress", () => ({
  BulkImportUploadProgress: () => <div data-testid="upload-progress" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportSpreadsheet", () => ({
  BulkImportSpreadsheet: () => <div data-testid="spreadsheet" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportTableReview", () => ({
  BulkImportTableReview: () => <div data-testid="table-review" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportSetLocation", () => ({
  BulkImportSetLocation: () => <div data-testid="set-location" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportContinueModal", () => ({
  BulkImportContinueModal: () => <div data-testid="continue-modal" />,
}));
jest.mock("../../../pages/BulkImport/BulkImportUnfinishedTaskModal", () => ({
  BulkImportUnfinishedTaskModal: () => <div data-testid="unfinished-modal" />,
}));

// Default‐export mocks
jest.mock("../../../pages/BulkImport/BulkImportInstructionsModal", () => {
  const MockInstructionsModal = () => <div data-testid="instructions-modal" />;
  MockInstructionsModal.displayName = "BulkImportInstructionsModal";
  return MockInstructionsModal;
});
jest.mock("../../../pages/BulkImport/BulkImportDraftSavedIndicator", () => {
  const MockDraftIndicator = () => <div data-testid="draft-indicator" />;
  MockDraftIndicator.displayName = "DraftSaveIndicator";
  return MockDraftIndicator;
});

// Mock store constructor and hook
jest.mock("../../../pages/BulkImport/BulkImportStore");
jest.mock("../../../models/bulkImport/useGetBulkImportTask");

describe("BulkImport Component", () => {
  let storeMock;

  beforeEach(() => {
    localStorage.clear();
    jest.clearAllMocks();

    storeMock = {
      activeStep: 0,
      uploadedImages: [],
      spreadsheetData: [],
      submissionId: null,
      saveState: jest.fn(),
    };
    BulkImportStore.mockImplementation(() => storeMock);

    useGetBulkImportTask.mockReturnValue({ task: null });
  });

  const renderComponent = () => render(<BulkImport />);

  it("renders header and fixed child components", () => {
    renderComponent();
    expect(
      screen.getByRole("heading", { name: /BULK_IMPORT/i }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("upload-progress")).toBeInTheDocument();
    expect(screen.getByTestId("draft-indicator")).toBeInTheDocument();
    expect(screen.getByTestId("instructions-modal")).toBeInTheDocument();
  });

  it("shows only the image‐upload step when activeStep is 0", () => {
    storeMock.activeStep = 0;
    renderComponent();
    expect(screen.getByTestId("image-upload")).toBeVisible();
    expect(screen.queryByTestId("spreadsheet")).toBeNull();
    expect(screen.queryByTestId("table-review")).toBeNull();
    expect(screen.queryByTestId("set-location")).toBeNull();
  });

  it("shows the spreadsheet step when activeStep is 1", async () => {
    storeMock.activeStep = 1;
    renderComponent();
    await waitFor(() => {
      expect(screen.getByTestId("spreadsheet")).toBeVisible();
    });
    expect(screen.queryByTestId("image-upload")).not.toBeVisible();
  });

  it("shows table‐review at step 2 and set‐location at step 3", async () => {
    storeMock.activeStep = 2;
    renderComponent();
    await waitFor(() => {
      expect(screen.getByTestId("table-review")).toBeVisible();
    });

    jest.clearAllMocks();
    storeMock.activeStep = 3;
    renderComponent();
    await waitFor(() => {
      expect(screen.getByTestId("set-location")).toBeVisible();
    });
  });

  it("displays ContinueModal when there is a saved submissionId", () => {
    localStorage.setItem(
      "BulkImportStore",
      JSON.stringify({ submissionId: "abc123" }),
    );
    renderComponent();
    expect(screen.getByTestId("continue-modal")).toBeInTheDocument();
  });

  it("shows UnfinishedTaskModal for an in‐progress task", () => {
    localStorage.setItem("lastBulkImportTask", "task-xyz");
    useGetBulkImportTask.mockReturnValue({
      task: {
        status: "processing",
        sourceName: "file1",
        dateCreated: "2025-07-02",
      },
    });
    renderComponent();
    expect(screen.getByTestId("unfinished-modal")).toBeInTheDocument();
  });

  it("does not show UnfinishedTaskModal when task is complete", () => {
    localStorage.setItem("lastBulkImportTask", "task-xyz");
    useGetBulkImportTask.mockReturnValue({
      task: {
        status: "complete",
        sourceName: "file1",
        dateCreated: "2025-07-02",
      },
    });
    renderComponent();
    expect(screen.queryByTestId("unfinished-modal")).toBeNull();
  });
});
