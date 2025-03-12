import React from "react";
import { screen } from "@testing-library/react";
import Progress from "../../../components/home/Progress";
import { renderWithProviders } from "../../../utils/utils";

const renderWithIntl = (ui) => {
  return renderWithProviders(ui);
};

describe("Progress Component", () => {
  const defaultProps = {
    name: "Test Project",
    encounters: 5,
    progress: 50,
    href: "/projects/123",
  };

  test("renders the component correctly with given props", () => {
    renderWithIntl(<Progress {...defaultProps} />);

    expect(screen.getByText("Test Project")).toBeInTheDocument();
    expect(screen.getByText("5 " + "ENCOUNTERS")).toBeInTheDocument();
    expect(screen.getByRole("link")).toHaveAttribute("href", "/projects/123");
  });

  test("applies disabled styles when disabled is true", () => {
    renderWithIntl(<Progress {...defaultProps} disabled={true} />);

    const link = screen.getByRole("link");
    expect(link).toHaveStyle("color: grey");
    expect(link).toHaveStyle("cursor: default");
  });

  test("removes underline when noUnderline is true", () => {
    renderWithIntl(<Progress {...defaultProps} noUnderline={true} />);

    const link = screen.getByRole("link");
    expect(link).toHaveStyle("text-decoration: none");
  });

  test("opens link in a new tab when newTab is true", () => {
    renderWithIntl(<Progress {...defaultProps} newTab={true} />);

    const link = screen.getByRole("link");
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noreferrer");
  });

  test("renders progress bar correctly", () => {
    renderWithIntl(<Progress {...defaultProps} progress={75} />);

    const progressBar = screen.getByText("75%");
    expect(progressBar).toBeInTheDocument();
  });

  test("renders correctly when progress is 0", () => {
    renderWithIntl(<Progress {...defaultProps} progress={0} />);

    const progressBar = screen.getByText("0%");
    expect(progressBar).toBeInTheDocument();
  });

  test("renders correctly when progress is 100", () => {
    renderWithIntl(<Progress {...defaultProps} progress={100} />);

    const progressBar = screen.getByText("100%");
    expect(progressBar).toBeInTheDocument();
  });
});
