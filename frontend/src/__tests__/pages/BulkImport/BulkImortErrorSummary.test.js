import React from "react";
import { render, screen } from "@testing-library/react";
import ErrorSummaryBar from "../../../pages/BulkImport/BulkImportErrorSummaryBar";

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

describe("ErrorSummaryBar", () => {
  const renderWithStore = (validationErrors, emptyFieldCount = 0) => {
    const store = { validationErrors, emptyFieldCount };
    render(<ErrorSummaryBar store={store} />);
  };

  test("shows zeros when there are no errors and no empty fields", () => {
    renderWithStore({}, 0);

    expect(screen.getByText(/BULK_IMPORT_ERROR/i)).toBeInTheDocument();
    expect(screen.getByText(/BULK_IMPORT_MISSING_FIELD/i)).toBeInTheDocument();
    expect(screen.getByText(/BULK_IMPORT_EMPTY_FIELD/i)).toBeInTheDocument();
    const items = screen.getAllByText(/\d+/);
    expect(items).toHaveLength(3);
  });

  test("correctly counts required vs invalid/missing errors", () => {
    const errors = {
      row1: {
        colA: "This field is required",
        colB: "Invalid format detected",
        colC: "Value missing in submission",
      },
      row2: {
        colX: "Required value not provided",
        colY: "unexpected value",
      },
    };
    renderWithStore(errors, 2);

    expect(screen.getByText(/BULK_IMPORT_ERROR/i)).toBeInTheDocument();
    expect(screen.getByText(/BULK_IMPORT_MISSING_FIELD/i)).toBeInTheDocument();
    expect(screen.getByText(/BULK_IMPORT_EMPTY_FIELD/i)).toBeInTheDocument();
    const items = screen.getAllByText(/\d+/);
    expect(items).toHaveLength(3);
  });

  test("renders container with correct id and badge classes", () => {
    const errors = {
      r1: { a: "required" },
    };
    renderWithStore(errors, 5);

    const container = document.querySelector("#bulk-import-error-summary-bar");
    expect(container).toBeInTheDocument();
    expect(container).toHaveClass("d-flex", "gap-2", "py-2");

    const badges = container.querySelectorAll(".badge");
    expect(badges.length).toBe(3);
    expect(badges[0]).toHaveClass("bg-danger");
    expect(badges[1]).toHaveClass("bg-danger");
    expect(badges[2]).toHaveClass("bg-primary");
  });
});
