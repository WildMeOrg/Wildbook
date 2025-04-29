import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import AddAnnotationModal from "../../components/AddAnnotationModal";
import { renderWithProviders } from "../../utils/utils";

describe("AddAnnotationModal", () => {
  const renderComponent = (props) => {
    return renderWithProviders(<AddAnnotationModal {...props} />);
  };

  test("renders modal title and close button", () => {
    renderComponent({
      showModal: true,
      setShowModal: jest.fn(),
      incomplete: false,
      error: null,
    });
    expect(screen.getByText("SUBMISSION_FAILED")).toBeInTheDocument();
    expect(screen.getByText("SESSION_CLOSE")).toBeInTheDocument();
  });

  test("displays missing required fields message when incomplete is true", () => {
    renderComponent({
      showModal: true,
      setShowModal: jest.fn(),
      incomplete: true,
      error: null,
    });
    expect(
      screen.getByText(
        "Missing required fields. Review the form and try again.",
      ),
    ).toBeInTheDocument();
  });

  test("displays correct error messages for invalid fields", () => {
    const errors = [{ code: "INVALID", fieldName: "testField" }];
    renderComponent({
      showModal: true,
      setShowModal: jest.fn(),
      incomplete: false,
      error: errors,
    });
    expect(
      screen.getByText(new RegExp(`BEERROR_INVALID\\s*testField`, "i")),
    ).toBeInTheDocument();
  });

  test("closes modal when close button is clicked", () => {
    const setShowModalMock = jest.fn();
    renderComponent({
      showModal: true,
      setShowModal: setShowModalMock,
      incomplete: false,
      error: null,
    });
    fireEvent.click(screen.getByText("SESSION_CLOSE"));
    expect(setShowModalMock).toHaveBeenCalledWith(false);
  });
});
