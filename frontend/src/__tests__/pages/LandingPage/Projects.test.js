import React from "react";
import { screen } from "@testing-library/react";
import Projects from "../../../components/home/Projects";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/home/Progress", () => {
  const React = require("react");
  const Progress = ({
    name,
    encounters,
    progress,
    href,
    noUnderline,
    newTab,
  }) =>
    React.createElement("div", { "data-testid": "progress-component" }, [
      React.createElement("span", {}, name),
      React.createElement("span", {}, encounters),
      React.createElement("span", {}, progress),
      React.createElement("span", {}, href),
      React.createElement("span", {}, noUnderline),
      React.createElement("span", {}, newTab),
    ]);
  return Progress;
});

const renderWithIntl = (ui) => {
  return renderWithProviders(ui);
};

describe("Projects Component", () => {
  const mockData = [
    {
      name: "Project 1",
      numberEncounters: 5,
      percentComplete: 80,
      id: "123",
    },
    {
      name: "Project 2",
      numberEncounters: 10,
      percentComplete: 60,
      id: "456",
    },
  ];

  test("renders the Projects component correctly", () => {
    renderWithIntl(<Projects data={mockData} />);

    expect(screen.getByText("HOME_VIEW_PROJECT_1")).toBeInTheDocument();
    expect(screen.getByText("HOME_VIEW_PROJECT_2")).toBeInTheDocument();
    expect(screen.getByText("SEE_ALL")).toBeInTheDocument();
  });

  test("renders progress components when data is available", () => {
    renderWithIntl(<Projects data={mockData} />);

    const progressComponents = screen.getAllByTestId("progress-component");
    expect(progressComponents).toHaveLength(mockData.length);
  });

  test("renders no projects message when data is empty", () => {
    renderWithIntl(<Projects data={[]} />);

    expect(screen.getByText("HOME_NO_PROJECT")).toBeInTheDocument();
  });

  test("renders the 'See All' link correctly", () => {
    renderWithIntl(<Projects data={mockData} />);

    const seeAllLink = screen.getByText("SEE_ALL").closest("a");
    expect(seeAllLink).toHaveAttribute("href", "/projects/projectList.jsp");
    expect(seeAllLink).toHaveAttribute("target", "_blank");
  });

  test("renders correctly when data is undefined", () => {
    renderWithIntl(<Projects data={undefined} />);

    expect(screen.getByText("HOME_NO_PROJECT")).toBeInTheDocument();
  });
});
