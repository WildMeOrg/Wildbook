import React from "react";
import { screen } from "@testing-library/react";
import LatestActivity from "../../../components/home/LatestActivityItem";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/BrutalismButton", () => {
  const React = require("react");
  const MockBrutalismButton = ({ children, disabled }) =>
    React.createElement("button", { disabled }, children);
  MockBrutalismButton.displayName = "BrutalismButton";
  return MockBrutalismButton;
});

describe("LatestActivity Component", () => {
  const renderComponent = (props) => {
    return renderWithProviders(<LatestActivity {...props} />);
  };

  it("renders component with all props", () => {
    renderComponent({
      name: "activity_name",
      num: 5,
      date: "2025-03-12",
      text: "Some activity",
      latestId: "123",
      disabled: false,
    });

    expect(screen.getByText("activity_name")).toBeInTheDocument();
    expect(screen.getByText("2025-03-12")).toBeInTheDocument();
    expect(screen.getByRole("button")).not.toBeDisabled();
  });

  it("renders component without num", () => {
    renderComponent({
      name: "activity_name",
      date: "2025-03-12",
      text: "Some activity",
      latestId: "123",
      disabled: false,
    });

    expect(screen.queryByText(/HOME_FILES_LOADED/)).not.toBeInTheDocument();
  });

  it("renders disabled button when disabled is true", () => {
    renderComponent({
      name: "activity_name",
      date: "2025-03-12",
      text: "Some activity",
      latestId: "123",
      disabled: true,
    });

    expect(screen.getByRole("button")).toBeDisabled();
  });

  it("renders NONE when text is empty", () => {
    renderComponent({
      name: "activity_name",
      date: "2025-03-12",
      latestId: "123",
      disabled: false,
    });

    expect(screen.getByText("NONE")).toBeInTheDocument();
  });
});
