import React from "react";
import { render, screen, fireEvent, act } from "@testing-library/react";
import { BulkImportSetLocation } from "../../../pages/BulkImport/BulkImportSetLocation";
import usePostBulkImport from "../../../models/bulkImport/usePostBulkImport";
import { BulkImportStore } from "../../../pages/BulkImport/BulkImportStore";
import { allRequiredColumns } from "../../../pages/BulkImport/BulkImportConstants";
import { BulkImportTableReview } from "../../../pages/BulkImport/BulkImportTableReview";

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
  const FailureModalMock = ({ show, errorMessage, onHide }) =>
    show ? (
      <div data-testid="failure-modal">
        {/* as the real modal: a string is shown, a list gets a generic line */}
        Failure: {typeof errorMessage === "string" ? errorMessage : null}
        <button onClick={onHide}>Review Data</button>
      </div>
    ) : null;

  FailureModalMock.displayName = "BulkImportFailureModal";
  return FailureModalMock;
});

jest.mock("../../../pages/BulkImport/EditableDataTable", () => {
  const EditableDataTable = () => <div data-testid="editable-table" />;
  EditableDataTable.displayName = "EditableDataTable";
  return EditableDataTable;
});

jest.mock("dayjs", () => {
  const dayjs = () => ({ format: () => "2025-07-14" });
  dayjs.extend = () => {};
  return dayjs;
});

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
      setSubmissionTransportError: jest.fn(),
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
    submitMock.mockRejectedValue({
      response: { status: 400, data: { errors: "error occurred" } },
    });
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.setSubmissionTransportError).toHaveBeenCalledWith(
      "error occurred",
    );
    expect(store.setSubmissionErrors).not.toHaveBeenCalled();
    expect(await screen.findByTestId("failure-modal")).toBeInTheDocument();
  });

  it("a 400 with a list of errors clears any earlier message", async () => {
    const errors = [{ rowNumber: 0, fieldName: "Encounter.genus" }];
    submitMock.mockRejectedValue({
      response: { status: 400, data: { errors } },
    });
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.setSubmissionErrors).toHaveBeenCalledWith(errors);
    expect(store.setSubmissionTransportError).toHaveBeenCalledWith(null);
    expect(store.setSubmissionTransportError).not.toHaveBeenCalledWith(
      expect.any(String),
    );
  });

  it("handles unexpected error and shows failure modal", async () => {
    submitMock.mockImplementation(() => Promise.reject({ response: {} }));
    render(<BulkImportSetLocation store={store} />);

    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });

    expect(store.setSubmissionTransportError).toHaveBeenCalledWith(
      "An unexpected error occurred during import",
    );
    expect(store.setSubmissionErrors).not.toHaveBeenCalled();
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
    const startLoading = loadingElements[1];
    expect(startLoading).toBeInTheDocument();
    expect(startLoading.closest("button")).toBeDisabled();
  });
});

describe("BulkImportSetLocation duplicate submission (409)", () => {
  const ALREADY =
    "This upload was already submitted. Start a new upload to import again.";
  let store;

  beforeEach(() => {
    jest.clearAllMocks();
    localStorage.clear();
    store = new BulkImportStore();
    store._rawColumns = ["col1"];
    store._columnsDef = [...allRequiredColumns];
    store._spreadsheetUploadProgress = 100;
    store._rawData = [["data1"]];
    store.setActiveStep(3);
    store.saveState();
    usePostBulkImport.mockReturnValue({
      submit: jest.fn().mockRejectedValue({
        response: {
          status: 409,
          data: { errors: [{ type: "DUPLICATE_IMPORT" }] },
        },
      }),
      isLoading: false,
    });
  });

  const clickStart = async () => {
    render(<BulkImportSetLocation store={store} />);
    await act(async () => {
      fireEvent.click(screen.getByText(/START_BULK_IMPORT/));
    });
  };

  it("shows already-submitted message on 409", async () => {
    await clickStart();

    expect(await screen.findByTestId("failure-modal")).toHaveTextContent(
      ALREADY,
    );
    expect(store.submissionTransportError).toBe(ALREADY);
    expect(typeof store.submissionErrors).not.toBe("string");
    expect(localStorage.getItem("BulkImportStore")).not.toBeNull();
  });

  it("dismissing the 409 modal shows review with the draft", async () => {
    const rawBefore = JSON.stringify(store.rawData);
    await clickStart();

    fireEvent.click(await screen.findByText("Review Data"));
    expect(store.activeStep).toBe(2);

    expect(() => render(<BulkImportTableReview store={store} />)).not.toThrow();
    expect(JSON.stringify(store.rawData)).toBe(rawBefore);
  });

  it("a 500 with an {error} body does not break the review table", async () => {
    usePostBulkImport.mockReturnValue({
      submit: jest.fn().mockRejectedValue({
        response: {
          status: 500,
          data: { error: "java.lang.IllegalStateException: x" },
        },
      }),
      isLoading: false,
    });
    await clickStart();

    expect(await screen.findByTestId("failure-modal")).toHaveTextContent(
      "An unexpected error occurred during import",
    );
    expect(typeof store.submissionErrors).not.toBe("string");
    fireEvent.click(await screen.findByText("Review Data"));
    expect(() => render(<BulkImportTableReview store={store} />)).not.toThrow();
  });

  it("a 409 that is not DUPLICATE_IMPORT is not 'already submitted'", async () => {
    const errors = [{ type: "SOMETHING_ELSE", code: "INVALID" }];
    usePostBulkImport.mockReturnValue({
      submit: jest.fn().mockRejectedValue({
        response: { status: 409, data: { errors } },
      }),
      isLoading: false,
    });
    await clickStart();

    expect(await screen.findByTestId("failure-modal")).not.toHaveTextContent(
      ALREADY,
    );
    expect(store.submissionTransportError).toBeNull();
    expect(store.submissionErrors).toEqual(errors);
  });

  it("resetToDefaults clears the already-submitted message", async () => {
    await clickStart();
    expect(store.submissionTransportError).toBe(ALREADY);

    store.resetToDefaults();
    expect(store.submissionTransportError).toBeNull();
  });
});
