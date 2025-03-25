import React from "react";
import { screen } from "@testing-library/react";
import LatestData from "../../../components/home/LatestData";
import { formatDate } from "../../../utils/formatters";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/DiamondCard", () => {
  const React = require("react");
  const MockDiamondCard = () =>
    React.createElement("div", { "data-testid": "diamond-card" });
  MockDiamondCard.displayName = "Sediamond-card";
  return MockDiamondCard;
});

jest.mock("../../../components/CircledMoreButton", () => {
  const React = require("react");
  const MockCircledMoreButton = () =>
    React.createElement("div", { "data-testid": "more-button" });
  MockCircledMoreButton.displayName = "more-button";
  return MockCircledMoreButton;
});

jest.mock("../../../utils/formatters", () => ({
  formatDate: jest.fn(),
}));

describe("LatestData Component", () => {
  const mockData = [
    { id: 1, date: "2025-03-10", taxonomy: "Bird", numberAnnotations: 5 },
    { id: 2, date: "2025-03-11", taxonomy: "Fish", numberAnnotations: 3 },
  ];
  const mockUsername = "testUser";

  test("renders the LatestData component", () => {
    renderWithProviders(
      <LatestData data={mockData} username={mockUsername} loading={false} />,
    );

    expect(screen.getByText("HOME_LATEST_DATA")).toBeInTheDocument();
  });

  test("renders correct number of DiamondCard components", () => {
    renderWithProviders(
      <LatestData data={mockData} username={mockUsername} loading={false} />,
    );

    const diamondCards = screen.getAllByTestId("diamond-card");
    expect(diamondCards.length).toBe(2);
  });

  test("renders the More button with correct href", () => {
    renderWithProviders(
      <LatestData data={mockData} username={mockUsername} loading={false} />,
    );

    const moreButton = screen.getByTestId("more-button");
    expect(moreButton).toBeInTheDocument();
  });

  test("formats date correctly", () => {
    formatDate.mockReturnValue("March 10, 2025");

    renderWithProviders(
      <LatestData data={mockData} username={mockUsername} loading={false} />,
    );

    expect(formatDate).toHaveBeenCalledWith(mockData[0].date, true);
    expect(formatDate).toHaveBeenCalledWith(mockData[1].date, true);
  });
});
