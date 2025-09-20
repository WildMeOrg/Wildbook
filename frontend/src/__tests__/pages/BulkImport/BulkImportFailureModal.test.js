import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import FailureModal from "../../../pages/BulkImport/BulkImportFailureModal";

describe("FailureModal", () => {
  const onHide = jest.fn();

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("does not render when show is false", () => {
    render(
      <IntlProvider locale="en" messages={{}}>
        <FailureModal show={false} onHide={onHide} errorMessage="ignored" />
      </IntlProvider>,
    );

    expect(screen.queryByText("BULK_IMPORT_FAILED")).toBeNull();
  });

  it("renders title and description when show is true", () => {
    render(
      <IntlProvider locale="en" messages={{}}>
        <FailureModal show={true} onHide={onHide} errorMessage="ignored" />
      </IntlProvider>,
    );

    expect(screen.getByText("BULK_IMPORT_FAILED")).toBeInTheDocument();
    expect(screen.getByText("BULK_IMPORT_FAILED_DESC")).toBeInTheDocument();
  });

  it("displays the BULK_IMPORT_ERROR_MESSAGE for array errorMessage", () => {
    render(
      <IntlProvider locale="en" messages={{}}>
        <FailureModal show={true} onHide={onHide} errorMessage={["a", "b"]} />
      </IntlProvider>,
    );

    expect(screen.getByText("BULK_IMPORT_ERROR_MESSAGE")).toBeInTheDocument();
  });

  it('calls onHide when "Review Data" button is clicked', () => {
    render(
      <IntlProvider locale="en" messages={{}}>
        <FailureModal show={true} onHide={onHide} errorMessage="x" />
      </IntlProvider>,
    );

    fireEvent.click(screen.getByText("REVIEW_DATA"));
    expect(onHide).toHaveBeenCalledTimes(1);
  });

  it('sets window.location.href to "/" when "Go to Home" is clicked', () => {
    let href = "";
    Object.defineProperty(window, "location", {
      configurable: true,
      get: () => ({
        get href() {
          return href;
        },
        set href(val) {
          href = val;
        },
      }),
    });

    render(
      <IntlProvider locale="en" messages={{}}>
        <FailureModal show={true} onHide={onHide} errorMessage="x" />
      </IntlProvider>,
    );

    fireEvent.click(screen.getByText("GO_HOME"));
    expect(href).toBe("/");
  });
});
