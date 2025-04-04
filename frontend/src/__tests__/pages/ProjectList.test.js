import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import axios from "axios";
import ProjectList from "../../pages/ProjectList";
import { renderWithProviders } from "../../utils/utils";

jest.mock("axios");

describe("ProjectList Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const mockUserResponse = { data: { displayName: "John Doe" } };
  const mockProjectsResponse = {
    data: {
      projects: [
        {
          id: 123,
          name: "Alpha Project",
          percentComplete: 50,
          numberEncounters: 5,
        },
        {
          id: 456,
          name: "Beta Project",
          percentComplete: 75,
          numberEncounters: 10,
        },
        {
          id: 789,
          name: "Gamma Project",
          percentComplete: 25,
          numberEncounters: 2,
        },
      ],
    },
  };

  function setup() {
    return renderWithProviders(<ProjectList />);
  }

  test("renders user name and project table after fetching data", async () => {
    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") {
        return Promise.resolve(mockUserResponse);
      }
      if (url === "/api/v3/projects") {
        return Promise.resolve(mockProjectsResponse);
      }
    });

    setup();

    expect(await screen.findByText(/PROJECTS_FOR/i)).toBeInTheDocument();
    expect(screen.getByText(/John Doe/i)).toBeInTheDocument();

    expect(screen.getByText("FILTER_PROJECT_NAME")).toBeInTheDocument();
    expect(screen.getByText("PROJECTS_ID")).toBeInTheDocument();

    expect(screen.getByText("Alpha Project")).toBeInTheDocument();
    expect(screen.getByText("Beta Project")).toBeInTheDocument();
    expect(screen.getByText("Gamma Project")).toBeInTheDocument();
  });

  test("displays 'No projects available' if the projects array is empty", async () => {
    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") {
        return Promise.resolve(mockUserResponse);
      }
      if (url === "/api/v3/projects") {
        return Promise.resolve({ data: { projects: [] } });
      }
    });

    setup();

    await screen.findByText(/John Doe/i);

    expect(screen.getByText("NO_PROJECTS")).toBeInTheDocument();
  });

  test("sorts projects by name (asc/desc) when the user clicks the header", async () => {
    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") return Promise.resolve(mockUserResponse);
      if (url === "/api/v3/projects")
        return Promise.resolve(mockProjectsResponse);
    });

    setup();
    await screen.findByText(/john Doe/i);

    const projectNameHeader = screen.getByText(/FILTER_PROJECT_NAME/i);
    fireEvent.click(projectNameHeader);

    const rowsAsc = screen
      .getAllByRole("row")
      .slice(1)
      .map((row) => row.textContent);

    expect(rowsAsc[0]).toMatch(/Alpha Project/);
    expect(rowsAsc[1]).toMatch(/Beta Project/);
    expect(rowsAsc[2]).toMatch(/Gamma Project/);

    fireEvent.click(projectNameHeader);

    const rowsDesc = screen
      .getAllByRole("row")
      .slice(1)
      .map((row) => row.textContent);

    expect(rowsDesc[0]).toMatch(/Gamma Project/);
    expect(rowsDesc[1]).toMatch(/Beta Project/);
    expect(rowsDesc[2]).toMatch(/Alpha Project/);
  });

  test("paginates projects correctly", async () => {
    const manyProjects = {
      data: {
        projects: Array.from({ length: 15 }, (_, idx) => ({
          id: idx + 1,
          name: `Project ${idx + 1}`,
          percentComplete: Math.floor(Math.random() * 100),
          numberEncounters: idx * 2,
        })),
      },
    };

    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") return Promise.resolve(mockUserResponse);
      if (url === "/api/v3/projects") return Promise.resolve(manyProjects);
    });

    setup();
    await screen.findByText(/John Doe/i);

    expect(screen.getAllByRole("row").length).toBe(11);

    const nextButton = screen.getAllByRole(
      "button",
      { name: "" },
      { selector: ".next-button" },
    )[1];
    fireEvent.click(nextButton);

    expect(screen.getAllByRole("row").length).toBe(6);
  });

  test("handles 'Go to' page input", async () => {
    axios.get.mockImplementation((url) => {
      if (url === "/api/v3/user") return Promise.resolve(mockUserResponse);
      if (url === "/api/v3/projects")
        return Promise.resolve(mockProjectsResponse);
    });

    setup();
    await screen.findByText(/John Doe/i);

    const gotoInput = screen.getByLabelText(/GO_TO/i);
    fireEvent.change(gotoInput, { target: { value: "2" } });
    fireEvent.keyDown(gotoInput, { key: "Enter", code: "Enter" });

    const alertSpy = jest.spyOn(window, "alert").mockImplementation(() => {});
    fireEvent.change(gotoInput, { target: { value: "2" } });
    fireEvent.keyDown(gotoInput, { key: "Enter", code: "Enter" });
    expect(alertSpy).toHaveBeenCalled(); // because there's no second page
  });
});
