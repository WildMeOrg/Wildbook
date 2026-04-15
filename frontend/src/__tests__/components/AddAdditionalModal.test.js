import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import AddAnnotationModal from "../../components/AddAnnotationModal";
import { renderWithProviders } from "../../utils/utils";

describe("AddAnnotationModal", () => {
  const renderComponent = (props = {}) => {
    const defaultProps = {
      showModal: true,
      setShowModal: jest.fn(),
      incomplete: false,
      error: null,
    };
    const finalProps = { ...defaultProps, ...props };
    const utils = renderWithProviders(<AddAnnotationModal {...finalProps} />);
    return { ...utils, props: finalProps };
  };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders modal title and close button", () => {
    renderComponent();

    expect(screen.getByText("SUBMISSION_FAILED")).toBeInTheDocument();
    expect(screen.getByText("SESSION_CLOSE")).toBeInTheDocument();
  });

  test("does not render modal content when showModal is false", () => {
    renderComponent({ showModal: false });

    expect(screen.queryByText("SUBMISSION_FAILED")).not.toBeInTheDocument();
    expect(screen.queryByText("SESSION_CLOSE")).not.toBeInTheDocument();
  });

  test("closes modal when close button is clicked", () => {
    const setShowModalMock = jest.fn();

    renderComponent({
      setShowModal: setShowModalMock,
    });

    fireEvent.click(screen.getByText("SESSION_CLOSE"));
    expect(setShowModalMock).toHaveBeenCalledWith(false);
  });
});
