import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { BulkImportTableReview } from "../../../pages/BulkImport/BulkImportTableReview";

jest.mock("react-intl", () => {
  const original = jest.requireActual("react-intl");
  return {
    ...original,
    FormattedMessage: ({ id }) => <span>{id}</span>,
    useIntl: () => ({
      formatMessage: ({ id }) => id,
    }),
  };
});

jest.mock("../../../pages/BulkImport/BulkImportImageUploadInfo", () => {
  const BulkImportImageUploadInfo = () => (
    <div data-testid="image-upload-info" />
  );
  BulkImportImageUploadInfo.displayName = "BulkImportImageUploadInfo";
  return { BulkImportImageUploadInfo };
});

jest.mock("../../../pages/BulkImport/BulkImportSpreadsheetUploadInfo", () => {
  const BulkImportSpreadsheetUploadInfo = () => (
    <div data-testid="spreadsheet-upload-info" />
  );
  BulkImportSpreadsheetUploadInfo.displayName =
    "BulkImportSpreadsheetUploadInfo";
  return { BulkImportSpreadsheetUploadInfo };
});

jest.mock("../../../pages/BulkImport/BulkImportSeeInstructionsButton", () => {
  const BulkImportSeeInstructionsButton = () => (
    <button data-testid="see-instructions">Instructions</button>
  );
  BulkImportSeeInstructionsButton.displayName =
    "BulkImportSeeInstructionsButton";
  return BulkImportSeeInstructionsButton;
});

jest.mock("../../../pages/BulkImport/EditableDataTable", () => {
  const EditableDataTable = () => <div data-testid="editable-table" />;
  EditableDataTable.displayName = "EditableDataTable";
  return EditableDataTable;
});

jest.mock("../../../pages/BulkImport/BulkImportErrorSummaryBar", () => {
  const BulkImportErrorSummaryBar = () => <div data-testid="error-summary" />;
  BulkImportErrorSummaryBar.displayName = "BulkImportErrorSummaryBar";
  return BulkImportErrorSummaryBar;
});

jest.mock("../../../components/MainButton", () => {
  const MainButton = (props) => (
    <button {...props} data-testid={props["data-testid"] || "main-button"}>
      {props.children}
    </button>
  );
  MainButton.displayName = "MainButton";
  return MainButton;
});

const createMockStore = (overrides = {}) => ({
  submissionErrors: [],
  missingRequiredColumns: [],
  validationErrors: {},
  spreadsheetUploadProgress: 100,
  setActiveStep: jest.fn(),
  ...overrides,
});

describe("BulkImportTableReview", () => {
  it("renders main layout and child components", () => {
    const store = createMockStore();
    render(<BulkImportTableReview store={store} />);

    expect(screen.getByText("BULK_IMPORT_TABLE_REVIEW")).toBeInTheDocument();
    expect(
      screen.getByText("BULK_IMPORT_TABLE_REVIEW_DESC"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("image-upload-info")).toBeInTheDocument();
    expect(screen.getByTestId("spreadsheet-upload-info")).toBeInTheDocument();
    expect(screen.getByTestId("editable-table")).toBeInTheDocument();
    expect(screen.getByTestId("error-summary")).toBeInTheDocument();
  });

  it("displays missing required columns warning", () => {
    const store = createMockStore({
      missingRequiredColumns: ["fieldA", "fieldB"],
    });

    render(<BulkImportTableReview store={store} />);
    expect(
      screen.getByText("BULK_IMPORT_MISSING_REQUIRED_COLUMNS"),
    ).toBeInTheDocument();
    expect(screen.getByText(/fieldA,fieldB/)).toBeInTheDocument();
  });

  it("disables NEXT button if spreadsheet not fully uploaded", () => {
    const store = createMockStore({
      spreadsheetUploadProgress: 80,
    });

    render(<BulkImportTableReview store={store} />);
    expect(screen.getByRole("button", { name: "NEXT" })).toBeDisabled();
  });

  it("disables NEXT button if validation errors exist", () => {
    const store = createMockStore({
      validationErrors: { 1: { someField: "Invalid" } },
    });

    render(<BulkImportTableReview store={store} />);
    expect(screen.getByRole("button", { name: "NEXT" })).toBeDisabled();
  });

  it("calls setActiveStep correctly on button click", () => {
    const store = createMockStore();
    render(<BulkImportTableReview store={store} />);

    fireEvent.click(screen.getByText("PREVIOUS"));
    fireEvent.click(screen.getByText("NEXT"));

    expect(store.setActiveStep).toHaveBeenCalledWith(1);
    expect(store.setActiveStep).toHaveBeenCalledWith(3);
  });

  it("displays submission errors with correct message ids", () => {
    const store = createMockStore({
      submissionErrors: [
        {
          type: "INVALID_VALUE",
          fieldNames: ["Species"],
        },
        {
          type: "REQUIRED_VALUE",
          rowNumber: 1,
          fieldNames: ["Date"],
        },
      ],
    });

    render(<BulkImportTableReview store={store} />);

    expect(screen.getByText("SUBMISSION_ISSUE_HEADER")).toBeInTheDocument();
    expect(
      screen.getByText(/BULK_IMPORT_ERROR_INVALID_VALUE.*Species/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/BULK_IMPORT_ERROR_REQUIRED_VALUE/),
    ).toBeInTheDocument();
  });

  it("shows the server's reason for box errors only", () => {
    const store = createMockStore({
      submissionErrors: [
        {
          type: "INVALID_VALUE",
          rowNumber: 1,
          fieldName: "Encounter.mediaAsset0.bbox",
          details:
            "org.ecocean.api.bulk.BulkValidatorException: box 1000,0,296,342 exceeds image 1152x768",
        },
        {
          type: "INVALID_VALUE",
          rowNumber: 2,
          fieldName: "Encounter.mediaAsset1",
          details: "a.jpg has a box in another row but not here",
        },
        {
          type: "INVALID_VALUE",
          rowNumber: 0,
          fieldName: "Encounter.year",
          details: "org.ecocean.api.bulk.BulkValidatorException: bad year",
        },
      ],
    });

    render(<BulkImportTableReview store={store} />);

    expect(
      screen.getByText(
        /Encounter\.mediaAsset0\.bbox \(box 1000,0,296,342 exceeds image 1152x768\)/,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/\(a\.jpg has a box in another row but not here\)/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/BulkValidatorException/)).toBeNull();
    expect(screen.queryByText(/bad year/)).toBeNull();
  });
});
