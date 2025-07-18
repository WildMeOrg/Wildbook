import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { BulkImportSetLocation } from "../../../pages/BulkImport/BulkImportSetLocation";
import usePostBulkImport from "../../../models/bulkImport/usePostBulkImport";

jest.mock("../../../models/bulkImport/usePostBulkImport");
jest.mock("antd/es/tree-select", () => {
  const component = ({ value, onChange }) => (
    <div data-testid="tree-select">
      <input
        data-testid="tree-select-input"
        value={value}
        onChange={(e) =>
          onChange([e.target.value], null, {
            triggerValue: e.target.value,
            checked: true,
          })
        }
      />
    </div>
  );
  component.displayName = "TreeSelect";
  return component;
});
jest.mock("../../../pages/BulkImport/BulkImportSuccessModal", () => {
  const SuccessModalMock = ({ show, fileName, submissionId, lastEdited }) =>
    show ? (
      <div data-testid="success-modal">
        Success: {fileName} {submissionId} {lastEdited}
      </div>
    ) : null;

  SuccessModalMock.displayName = "BulkImportSuccessModal";
  return SuccessModalMock;
});

jest.mock("../../../pages/BulkImport/BulkImportFailureModal", () => {
  const FailureModalMock = ({ show, errorMessage }) =>
    show ? (
      <div data-testid="failure-modal">Failure: {errorMessage}</div>
    ) : null;

  FailureModalMock.displayName = "BulkImportFailureModal";
  return FailureModalMock;
});

jest.mock("dayjs", () => () => ({ format: () => "2025-07-14" }));

describe("BulkImportSetLocation", () => {
  let store;
  let submitMock;

  beforeEach(() => {
    jest.clearAllMocks();

    store = {
      submissionId: null,
      spreadsheetData: [{ "Encounter.locationID": "loc1" }],
      locationIDOptions: [{ value: "loc1", children: [{ value: "child1" }] }],
      locationID: [],
      setLocationID: jest.fn(),
      updateRawFromNormalizedRow: jest.fn(),
      clearSubmissionErrors: jest.fn(),
      rawColumns: ["col1"],
      rawData: [["data1"]],
      spreadsheetFileName: "test.xlsx",
      skipDetection: false,
      setSkipDetection: jest.fn(),
      skipIdentification: false,
      setSkipIdentification: jest.fn(),
      spreadsheetUploadProgress: 100,
      validationErrors: [],
      missingRequiredColumns: [],
      setActiveStep: jest.fn(),
      setSubmissionErrors: jest.fn(),
    };

    submitMock = jest.fn();
    usePostBulkImport.mockReturnValue({ submit: submitMock, isLoading: false });
  });

  it("renders title, description, and buttons", () => {
    render(<BulkImportSetLocation store={store} />);

    expect(screen.getByText("BULK_IMPORT_SET_LOCATION")).toBeInTheDocument();
    expect(
      screen.getByText(/BULK_IMPORT_SET_LOCATION_DESC/),
    ).toBeInTheDocument();
    expect(screen.getByText(/PREVIOUS/)).toBeInTheDocument();
    expect(screen.getByText(/START_BULK_IMPORT/)).toBeInTheDocument();
  });

  it("calls setActiveStep(2) when Previous button is clicked", () => {
    render(<BulkImportSetLocation store={store} />);

    fireEvent.click(screen.getByText(/PREVIOUS/));
    expect(store.setActiveStep).toHaveBeenCalledWith(2);
  });

  it("updates location selection on tree-select change", () => {
    render(<BulkImportSetLocation store={store} />);

    const input = screen.getByTestId("tree-select-input");
    fireEvent.change(input, { target: { value: "loc1" } });

    expect(store.setLocationID).toHaveBeenCalledWith(
      expect.arrayContaining(["loc1", "child1"]),
    );
  });

  it("submits import and shows success modal on status 200", async () => {
    submitMock.mockResolvedValue({
      status: 200,
      data: { bulkImportId: "123" },
    });
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.updateRawFromNormalizedRow).toHaveBeenCalled();
    expect(store.clearSubmissionErrors).toHaveBeenCalled();

    expect(await screen.findByTestId("success-modal")).toBeInTheDocument();
    const modal = await screen.findByTestId("success-modal");
    expect(modal).toHaveTextContent("test.xlsx");
    expect(modal).toHaveTextContent("2025-07-14");

    expect(localStorage.getItem("lastBulkImportTask")).toBe("123");
  });

  it("submits import and shows failure modal on status 400", async () => {
    submitMock.mockResolvedValue({
      status: 400,
      data: { errors: "error occurred" },
    });
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.setSubmissionErrors).toHaveBeenCalledWith("error occurred");
    expect(await screen.findByTestId("failure-modal")).toBeInTheDocument();
  });

  it("handles unexpected error and shows failure modal", async () => {
    submitMock.mockImplementation(() => Promise.reject({ response: {} }));
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.setSubmissionErrors).toHaveBeenCalledWith(
      "An unexpected error occurred during import",
    );
    expect(await screen.findByTestId("failure-modal")).toBeInTheDocument();
  });
  it("initially sets location based on spreadsheetData via reaction", () => {
    render(<BulkImportSetLocation store={store} />);
    expect(store.setLocationID).toHaveBeenCalledWith(
      expect.arrayContaining(["loc1", "child1"]),
    );
  });

  it("toggles skipDetection when checkbox is clicked", () => {
    render(<BulkImportSetLocation store={store} />);
    const checkbox = screen.getByLabelText("SKIP_DETECTION_AND_ID");
    fireEvent.click(checkbox);
    expect(store.setSkipDetection).toHaveBeenCalledWith(true);
  });

  it("toggles skipIdentification when checkbox is clicked", () => {
    render(<BulkImportSetLocation store={store} />);
    const checkbox = screen.getByLabelText("SKIP_ONLY_IDENTIFICATION");
    fireEvent.click(checkbox);
    expect(store.setSkipIdentification).toHaveBeenCalledWith(true);
  });

  it("disables START button when validation errors exist", () => {
    store.validationErrors = [{}];
    render(<BulkImportSetLocation store={store} />);
    const startBtn = screen.getByText(/START_BULK_IMPORT/).closest("button");
    expect(startBtn).toBeDisabled();
  });

  it("shows loading state on START button when isLoading is true", () => {
    usePostBulkImport.mockReturnValue({ submit: submitMock, isLoading: true });
    render(<BulkImportSetLocation store={store} />);
    const loadingElements = screen.getAllByText(/LOADING/);
    // Both Previous and Start buttons show LOADING; pick the second for Start
    const startLoading = loadingElements[1];
    expect(startLoading).toBeInTheDocument();
    expect(startLoading.closest("button")).toBeDisabled();
  });
});
