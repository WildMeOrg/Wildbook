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
  clearSubmissionErrors: jest.fn(),
  setSpreadsheetError: jest.fn(),
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

  test("file input triggers file upload on change", () => {
    const file = new File(["dummy content"], "test.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const fileInput = screen.getByTestId("spreadsheet-input");

    fireEvent.change(fileInput, {
      target: { files: [file] },
    });

    expect(mockStore.setSpreadsheetFileName).toHaveBeenCalledWith("test.xlsx");
    expect(mockStore.setSpreadsheetUploadProgress).toHaveBeenCalledWith(0);
  });

  test("displays alert for invalid file type on drop", () => {
    window.alert = jest.fn();
    const file = new File(["invalid"], "invalid.txt", { type: "text/plain" });

    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;

    fireEvent.drop(dropArea, {
      dataTransfer: { files: [file] },
      preventDefault: jest.fn(),
    });

    expect(window.alert).toHaveBeenCalledWith(
      "Please upload a valid CSV or XLSX file.",
    );
  });
  test("file name is set and progress initialized to 0 on upload", () => {
    const file = new File(["dummy"], "upload.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });

    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const input = screen.getByTestId("spreadsheet-input");

    fireEvent.change(input, { target: { files: [file] } });

    expect(mockStore.setSpreadsheetFileName).toHaveBeenCalledWith(
      "upload.xlsx",
    );
    expect(mockStore.setSpreadsheetUploadProgress).toHaveBeenCalledWith(0);
  });
  test("clicking close clears all relevant store values", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 60,
      spreadsheetFileName: "abc.xlsx",
    };
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={store} />,
    );

    fireEvent.click(container.querySelector("#close-button"));

    expect(store.setRawData).toHaveBeenCalledWith([]);
    expect(store.setColumnsDef).toHaveBeenCalledWith([]);
    expect(store.setValidationErrors).toHaveBeenCalledWith({});
  });

  test("upload button triggers file input click", () => {
    const clickMock = jest.fn();
    document.getElementById = jest.fn(() => ({ click: clickMock }));

    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    fireEvent.click(screen.getByText("BROWSE"));
    expect(clickMock).toHaveBeenCalled();
  });
  test("upload complete message shown only when progress is 100", () => {
    const store = { ...mockStore, spreadsheetUploadProgress: 100 };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(
      screen.getByText("BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE"),
    ).toBeInTheDocument();
  });
  test("does not show complete text when upload not 100%", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    expect(
      screen.queryByText("BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE"),
    ).not.toBeInTheDocument();
  });
  test("file input has correct attributes", () => {
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={mockStore} />,
    );
    const input = container.querySelector("#spreadsheet-input");
    expect(input).toHaveAttribute("type", "file");
    expect(input).toHaveAttribute("accept", ".csv,.xlsx");
  });
  test("renders internationalized labels", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    expect(
      screen.getByText("BULK_IMPORT_UPLOAD_SPREADSHEET"),
    ).toBeInTheDocument();
  });
  test("instruction helper button is rendered", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    expect(screen.getByText("SEE_INSTRUCTIONS")).toBeInTheDocument();
  });
  test("progress bar displays percentage", () => {
    const store = { ...mockStore, spreadsheetUploadProgress: 65 };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(screen.getByText("65%")).toBeInTheDocument();
  });
  test("progress bar shows COMPLETE when done", () => {
    const store = { ...mockStore, spreadsheetUploadProgress: 100 };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(
      screen.getByText("BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE"),
    ).toBeInTheDocument();
  });
  test("handles empty workbook gracefully", async () => {
    const emptyFile = new File([""], "empty.xlsx");
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;

    fireEvent.drop(dropArea, {
      dataTransfer: { files: [emptyFile] },
      preventDefault: jest.fn(),
    });

    await waitFor(() => {
      expect(mockStore.setRawData).toHaveBeenCalledWith([]);
    });
  });
  test("handles non-standard spreadsheet format", async () => {
    const weirdFile = new File(["weird data"], "weird.xlsx");
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    fireEvent.drop(screen.getByText("BROWSE").parentElement, {
      dataTransfer: { files: [weirdFile] },
      preventDefault: jest.fn(),
    });

    await waitFor(() => {
      expect(mockStore.setSpreadsheetFileName).toHaveBeenCalled();
    });
  });
  test("browse button is keyboard accessible", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const button = screen.getByText("BROWSE").closest("button");
    expect(button).not.toBeNull();
    expect(button?.tabIndex).toBeGreaterThanOrEqual(0);
  });

  test("main container has correct ID", () => {
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={mockStore} />,
    );
    expect(
      container.querySelector("#bulk-import-spreadsheet"),
    ).toBeInTheDocument();
  });
  test("upload area has dashed border", () => {
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={mockStore} />,
    );
    const uploadArea = container.querySelector('[style*="border: 1px dashed"]');
    expect(uploadArea).toBeInTheDocument();
  });
  test("clearSubmissionErrors is called on file input change", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const file = new File(["dummy"], "file.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    const input = screen.getByTestId("spreadsheet-input");
    fireEvent.change(input, { target: { files: [file] } });
    expect(mockStore.clearSubmissionErrors).toHaveBeenCalled();
  });

  test("empty file change still resets filename and progress to 0", () => {
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const input = screen.getByTestId("spreadsheet-input");
    fireEvent.change(input, { target: { files: [] } });
    expect(mockStore.clearSubmissionErrors).toHaveBeenCalled();
    expect(mockStore.setSpreadsheetFileName).toHaveBeenCalledWith("");
    expect(mockStore.setSpreadsheetUploadProgress).toHaveBeenCalledWith(0);
  });

  test("dropping a CSV file sets filename and initializes progress", () => {
    window.alert = jest.fn();
    const csv = new File(["a,b,c"], "test.csv", { type: "text/csv" });
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;
    fireEvent.drop(dropArea, {
      dataTransfer: { files: [csv] },
      preventDefault: jest.fn(),
    });
    expect(mockStore.setSpreadsheetFileName).toHaveBeenCalledWith("test.csv");
    expect(mockStore.setSpreadsheetUploadProgress).toHaveBeenCalledWith(0);
    expect(window.alert).not.toHaveBeenCalled();
  });

  test("does not call progress setter when dropping invalid file type", () => {
    window.alert = jest.fn();
    const bad = new File(["x"], "bad.txt", { type: "text/plain" });
    renderWithProviders(<BulkImportSpreadsheet store={mockStore} />);
    const dropArea = screen.getByText("BROWSE").parentElement;
    fireEvent.drop(dropArea, {
      dataTransfer: { files: [bad] },
      preventDefault: jest.fn(),
    });
    expect(window.alert).toHaveBeenCalledWith(
      "Please upload a valid CSV or XLSX file.",
    );
    expect(mockStore.setSpreadsheetUploadProgress).not.toHaveBeenCalled();
  });

  test("when progress > 0, the uploaded filename is displayed", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 50,
      spreadsheetFileName: "abc.xlsx",
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(screen.getByText("abc.xlsx")).toBeInTheDocument();
  });

  test("when progress > 0, the initial drop area (BROWSE) is hidden", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 50,
      spreadsheetFileName: "abc.xlsx",
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(screen.queryByText("BROWSE")).not.toBeInTheDocument();
  });

  test("renders a progressbar element with role progressbar when uploading", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 65,
      spreadsheetFileName: "upload.xlsx",
    };
    renderWithProviders(<BulkImportSpreadsheet store={store} />);
    expect(screen.getByRole("progressbar")).toBeInTheDocument();
  });

  test("clicking close resets rawColumns to an empty array", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 50,
      spreadsheetFileName: "file.xlsx",
    };
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={store} />,
    );
    fireEvent.click(container.querySelector("#close-button"));
    expect(store.setRawColumns).toHaveBeenCalledWith([]);
  });

  test("clicking close also clears validation warnings", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 80,
      spreadsheetFileName: "warn.xlsx",
    };
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={store} />,
    );
    fireEvent.click(container.querySelector("#close-button"));
    expect(store.setValidationWarnings).toHaveBeenCalledWith({});
  });

  test("clicking close resets worksheet info to its initial state", () => {
    const store = {
      ...mockStore,
      spreadsheetUploadProgress: 75,
      spreadsheetFileName: "sheet.xlsx",
    };
    const { container } = renderWithProviders(
      <BulkImportSpreadsheet store={store} />,
    );
    fireEvent.click(container.querySelector("#close-button"));
    expect(store.setWorksheetInfo).toHaveBeenCalledWith(0, [], 0, 0, "");
  });
});
