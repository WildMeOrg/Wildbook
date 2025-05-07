import React from "react";
import { screen } from "@testing-library/react";
import PickUp from "../../../components/home/PickUpWhereYouLeft";
import { formatDate } from "../../../utils/formatters";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/home/LatestActivityItem", () => {
  const React = require("react");
  const LatestActivityItem = ({ name, num, date, disabled, latestId }) =>
    React.createElement("div", { "data-testid": name }, [
      React.createElement("span", {}, num),
      React.createElement("span", {}, date),
      React.createElement("span", {}, disabled ? "Disabled" : "Enabled"),
      React.createElement("span", {}, latestId),
    ]);
  return LatestActivityItem;
});

describe("PickUp Component", () => {
  const mockData = {
    latestMatchTask: {
      dateTimeCreated: new Date().toISOString(),
      id: "123",
      encounterId: "456",
    },
    latestBulkImportTask: {
      numberMediaAssets: 10,
      dateTimeCreated: new Date().toISOString(),
      id: "789",
    },
    latestIndividual: {
      dateTimeCreated: new Date().toISOString(),
      id: "101",
    },
  };

  const renderComponent = (data = mockData) => {
    return renderWithProviders(<PickUp data={data} />);
  };

  test("renders the PickUp component correctly", () => {
    renderComponent();

    expect(screen.getByText("HOME_PICK_UP_1")).toBeInTheDocument();
    expect(screen.getByText("HOME_PICK_UP_2")).toBeInTheDocument();
  });

  test("renders LatestActivityItem for bulk import", () => {
    renderComponent();
    const bulkImportItem = screen.getByTestId("HOME_LATEST_BULK_REPORT");

    expect(bulkImportItem).toBeInTheDocument();
    expect(bulkImportItem).toHaveTextContent("10");
    expect(bulkImportItem).toHaveTextContent(
      formatDate(mockData.latestBulkImportTask.dateTimeCreated, true),
    );
    expect(bulkImportItem).toHaveTextContent("/import.jsp?taskId=789");
  });

  test("renders LatestActivityItem for latest individual", () => {
    renderComponent();
    const latestIndividualItem = screen.getByTestId("HOME_LATEST_INDIVIDUAL");

    expect(latestIndividualItem).toBeInTheDocument();
    expect(latestIndividualItem).toHaveTextContent(
      formatDate(mockData.latestIndividual.dateTimeCreated, true),
    );
    expect(latestIndividualItem).toHaveTextContent("/individuals.jsp?id=101");
  });

  test("renders LatestActivityItem for latest matching action", () => {
    renderComponent();
    const latestMatchItem = screen.getByTestId("HOME_LATEST_MATCHING_ACTION");

    expect(latestMatchItem).toBeInTheDocument();
    expect(latestMatchItem).toHaveTextContent(
      formatDate(mockData.latestMatchTask.dateTimeCreated, true),
    );
    expect(latestMatchItem).toHaveTextContent("/iaResults.jsp?taskId=123");
  });

  test("generates the correct matchActionButtonUrl based on date", () => {
    const twoWeeksAgo = new Date();
    twoWeeksAgo.setDate(twoWeeksAgo.getDate() - 15);

    const modifiedData = {
      ...mockData,
      latestMatchTask: {
        ...mockData.latestMatchTask,
        dateTimeCreated: twoWeeksAgo.toISOString(),
      },
    };

    renderComponent(modifiedData);
    const latestMatchItem = screen.getByTestId("HOME_LATEST_MATCHING_ACTION");

    expect(latestMatchItem).toHaveTextContent(
      "/encounters/encounter.jsp?number=456",
    );
  });
});
