import React from "react";
import { screen } from "@testing-library/react";
import Report from "../../../components/home/Report";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/BrutalismButton", () => {
  const React = require("react");
  const BrutalismButton = ({ link, children }) =>
    React.createElement(
      "a",
      { "data-testid": "brutalism-button", href: link },
      children,
    );
  return BrutalismButton;
});

describe("Report Component", () => {
  test("renders the Report component correctly", () => {
    renderWithProviders(<Report />);

    expect(screen.getByText("SUBMIT_NEW_DATA")).toBeInTheDocument();
    expect(screen.getByText("REPORT_AN_ENCOUNTER")).toBeInTheDocument();
    expect(screen.getByText("BULK_REPORT")).toBeInTheDocument();
  });

  test("renders buttons with correct links", () => {
    renderWithProviders(<Report />);

    const buttons = screen.getAllByTestId("brutalism-button");
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toHaveAttribute("href", "/submit.jsp");
    expect(buttons[1]).toHaveAttribute("href", "/import/instructions.jsp");
  });
});
