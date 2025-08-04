import React from "react";
import { render, fireEvent, screen } from "../../../utils/utils";
import "@testing-library/jest-dom";
import ApplyToAllRowsModal from "../../../pages/BulkImport/BulkImportApplyToAllRowsModal";
import { IntlProvider } from "react-intl";

const messages = {
  BULK_IMPORT_APPLY_TO_ALL_ROWS_TITLE: "Apply to all rows",
  BULK_IMPORT_APPLY_TO_ALL_ROWS_BODY:
    "Apply {columnId} to all rows with {newValue}.",
  BULK_IMPORT_CLOSE: "Close",
  BULK_IMPORT_APPLY_TO_ALL: "Apply",
};

function setup(store, columnId = "colX", newValue = "valY") {
  return render(
    <IntlProvider locale="en" messages={messages}>
      <ApplyToAllRowsModal
        store={store}
        columnId={columnId}
        newValue={newValue}
      />
    </IntlProvider>,
  );
}

describe("ApplyToAllRowsModal", () => {
  let store;

  beforeEach(() => {
    store = {
      applyToAllRows: jest.fn(),
      setApplyToAllRowModalShow: jest.fn(),
      invalidateValidation: jest.fn(),
      validateSpreadsheet: jest.fn(() => ({
        errors: { row1: ["e1"] },
        warnings: { row1: ["w1"] },
      })),
      setValidationErrors: jest.fn(),
      setValidationWarnings: jest.fn(),
      applyToAllRowModalShow: false,
    };
  });

  it("does not render when show=false", () => {
    store.applyToAllRowModalShow = false;
    setup(store);
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("renders title, body and buttons when show=true", () => {
    store.applyToAllRowModalShow = true;
    setup(store, "C", "V");

    const dialog = screen.getByRole("dialog");
    expect(dialog).toBeInTheDocument();

    expect(screen.getByText("BULK_IMPORT_APPLY_TO_ALL")).toBeInTheDocument();

    expect(screen.getByText("BULK_IMPORT_CLOSE")).toBeInTheDocument();
    expect(screen.getByText("BULK_IMPORT_APPLY_TO_ALL")).toBeInTheDocument();
  });

  it("clicking Close hides modal", () => {
    store.applyToAllRowModalShow = true;
    setup(store);
    fireEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(store.setApplyToAllRowModalShow).toHaveBeenCalledWith(false);
  });

  it("clicking Apply calls store methods in order", () => {
    store.applyToAllRowModalShow = true;
    setup(store, "ColA", "ValB");
    fireEvent.click(screen.getByText("BULK_IMPORT_APPLY_TO_ALL"));

    expect(store.applyToAllRows).toHaveBeenCalledWith("ColA", "ValB");
    expect(store.setApplyToAllRowModalShow).toHaveBeenCalledWith(false);
    expect(store.invalidateValidation).toHaveBeenCalled();
    expect(store.validateSpreadsheet).toHaveBeenCalled();
    expect(store.setValidationErrors).toHaveBeenCalledWith({ row1: ["e1"] });
    expect(store.setValidationWarnings).toHaveBeenCalledWith({ row1: ["w1"] });
  });
});
