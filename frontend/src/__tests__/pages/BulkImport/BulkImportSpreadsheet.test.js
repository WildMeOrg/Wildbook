import React from "react";
import { BulkImportSpreadsheet } from "../../../pages/BulkImport/BulkImportSpreadsheet";
import { renderWithProviders } from "../../../utils/utils";
import { fireEvent, screen, waitFor } from "@testing-library/react";

const mockStore = {
  spreadsheetUploadProgress: 0,
  spreadsheetFileName: "",
  setSpreadsheetFileName: jest.fn(),
  setSpreadsheetUploadProgress: jest.fn(),
  setRawData: jest.fn(),
  setRawColumns: jest.fn(),
  setColumnsDef: jest.fn(),
  setWorksheetInfo: jest.fn(),
  setSpreadsheetData: jest.fn(),
  validateSpreadsheet: jest.fn(() => ({ errors: {}, warnings: {} })),
  setValidationErrors: jest.fn(),
  setValidationWarnings: jest.fn(),
  applyDynamicValidationRules: jest.fn(),
  setActiveStep: jest.fn(),
};

describe("BulkImportSpreadsheet", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders initial upload state", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    expect(
      screen.getByText("BULK_IMPORT_UPLOAD_SPREADSHEET"),
    ).toBeInTheDocument();
    expect(screen.getByText("BROWSE")).toBeInTheDocument();
  });

  test("clicking previous sets active step to 0", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    fireEvent.click(screen.getByText("PREVIOUS"));
    expect(mockStore.setActiveStep).toHaveBeenCalledWith(0);
  });

  test("next button disabled when progress is not 100", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const nextButton = screen.getByText("NEXT").closest("button");
    expect(nextButton).toBeDisabled();
  });

  test("next button enabled when progress is 100", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 100,
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    const nextButton = screen.getByText("NEXT").closest("button");
    expect(nextButton).not.toBeDisabled();
  });

  test("clicking next sets active step to 2 when upload complete", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 100,
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    fireEvent.click(screen.getByText("NEXT"));
    expect(store.setActiveStep).toHaveBeenCalledWith(2);
  });

  test("drag over and leave updates dragging state visually", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;
    fireEvent.dragOver(dropArea);
    fireEvent.dragLeave(dropArea);
  });

  test("drop event triggers file processing", async () => {
    const file = new File(["test content"], "test.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;

    fireEvent.drop(dropArea, {
      dataTransfer: {
        files: [file],
      },
      preventDefault: jest.fn(),
    });

    await waitFor(() => {
      expect(mockStore.setSpreadsheetFileName).toHaveBeenCalledWith(
        "test.xlsx",
      );
    });
  });

  test("clicking close resets spreadsheet state", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 50,
      spreadsheetFileName: "test.xlsx",
    };
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={store} />,
    );

    const closeIcon = container.querySelector("#close-button");
    expect(closeIcon).toBeInTheDocument();

    fireEvent.click(closeIcon);

    expect(store.setSpreadsheetUploadProgress).toHaveBeenCalledWith(0);
    expect(store.setSpreadsheetFileName).toHaveBeenCalledWith("");
    expect(store.setSpreadsheetData).toHaveBeenCalledWith([]);
  });

  test("displays upload complete text when progress is 100", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 100,
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(
      screen.getByText("BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE"),
    ).toBeInTheDocument();
  });

  test("upload input is hidden but accessible", () => {
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={mockStore} />,
    );
    const fileInput = container.querySelector("#spreadsheet-input");

    expect(fileInput).toBeInTheDocument();
    expect(fileInput).toHaveAttribute("type", "file");
    expect(fileInput).toHaveAttribute("accept", ".csv,.xlsx");
    expect(fileInput).toHaveStyle("display: none");
  });

  test("instruction button is rendered", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    expect(screen.getByText("SEE_INSTRUCTIONS")).toBeInTheDocument();
  });

  test("does not proceed if no file is dropped", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;

    fireEvent.drop(dropArea, {
      dataTransfer: {
        files: [],
      },
      preventDefault: jest.fn(),
    });

    expect(mockStore.setSpreadsheetFileName).not.toHaveBeenCalled();
  });
});
