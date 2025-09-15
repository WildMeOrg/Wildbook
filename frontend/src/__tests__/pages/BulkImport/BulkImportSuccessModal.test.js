import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import SuccessModal from "../../../pages/BulkImport/BulkImportSuccessModal";
import ThemeContext from "../../../ThemeColorProvider";

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id, defaultMessage }) => (
    <span>{defaultMessage || id}</span>
  ),
}));

jest.mock("react-router-dom", () => {
  const mockNavigate = jest.fn();
  return {
    __esModule: true,
    useNavigate: () => mockNavigate,
  };
});

describe("SuccessModal", () => {
  let originalLocation;

  const defaultProps = {
    show: true,
    onHide: jest.fn(),
    fileName: "myfile.xlsx",
    submissionId: "abc123",
    lastEdited: "2025-07-18 11:00",
  };

  const theme = {
    primaryColors: {
      primary100: "#eee",
      primary500: "#333",
    },
    wildMeColors: {
      cyan700: "#007599",
    },
    defaultColors: {
      white: "#fff",
    },
  };

  beforeAll(() => {
    originalLocation = window.location;
    delete window.location;
    window.location = { href: "" };
  });

  afterAll(() => {
    window.location = originalLocation;
  });

  it("does not render when show=false", () => {
    render(
      <ThemeContext.Provider value={theme}>
        <SuccessModal {...defaultProps} show={false} />
      </ThemeContext.Provider>,
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("renders title, description, file info and buttons when visible", () => {
    render(
      <ThemeContext.Provider value={theme}>
        <SuccessModal {...defaultProps} />
      </ThemeContext.Provider>,
    );

    expect(
      screen.getByText("Bulk Import Started Successfully"),
    ).toBeInTheDocument();

    expect(
      screen.getByText(
        "Your submission was successful and is now being processed in the background. You can track the progress of this task from the bulk import task details page.",
      ),
    ).toBeInTheDocument();

    expect(screen.getByText("myfile.xlsx")).toHaveClass("fw-bold");

    const lastLine = screen.getByText("2025-07-18 11:00");
    expect(lastLine).toBeInTheDocument();
    expect(screen.getByText("See Details")).toBeInTheDocument();
    expect(screen.getByText("Go to Home")).toBeInTheDocument();
  });

  it("calls onHide when clicking outside or Close (Modal backdrop)", () => {
    render(
      <ThemeContext.Provider value={theme}>
        <SuccessModal {...defaultProps} />
      </ThemeContext.Provider>,
    );
    const buttons = screen.getAllByRole("button");
    fireEvent.click(buttons[0]);
    expect(defaultProps.onHide).toHaveBeenCalled();
  });

  it('navigates to task details URL when "See Details" is clicked', () => {
    render(
      <ThemeContext.Provider value={theme}>
        <SuccessModal {...defaultProps} />
      </ThemeContext.Provider>,
    );
    fireEvent.click(screen.getByText("See Details"));
    expect(window.location.href).toBe(
      `${process.env.PUBLIC_URL}/bulk-import-task?id=abc123`,
    );
  });
});
