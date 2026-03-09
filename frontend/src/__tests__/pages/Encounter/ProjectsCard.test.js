/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
  useIntl: () => ({
    formatMessage: ({ id }) => id,
  }),
}));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({
      wildMeColors: {
        cyan700: "#00abc2",
      },
    }),
  };
});

jest.mock("../../../components/icons/ProjectsIcon", () => (props) => (
  <div data-testid="projects-icon" {...props}>
    ProjectsIcon
  </div>
));
jest.mock("../../../components/icons/RemoveIcon", () => (props) => (
  <span data-testid="remove-icon" {...props}>
    X
  </span>
));

jest.mock("../../../components/MainButton", () => (props) => (
  <button
    data-testid={props["data-testid"] || "main-button"}
    onClick={props.onClick}
    disabled={props.disabled}
  >
    {props.children}
  </button>
));

jest.mock("react-select", () => {
  const React = require("react");
  return (props) => (
    <div
      data-testid="react-select"
      onClick={() => {
        if (props.onChange) {
          props.onChange([{ value: "p3", label: "Project 3" }]);
        }
      }}
    >
      ReactSelectMock
    </div>
  );
});

import { ProjectsCard } from "../../../pages/Encounter/ProjectsCard";

const makeStore = (overrides = {}) => ({
  access: "write",
  siteSettingsData: {
    projectsForUser: {
      p1: "Project 1",
      p2: "Project 2",
      p3: "Project 3",
    },
  },
  encounterData: {
    projects: ["p1", "p2"],
  },
  selectedProjects: [],
  setSelectedProjects: jest.fn(),
  addEncounterToProject: jest.fn(),
  removeProjectFromEncounter: jest.fn(),
  ...overrides,
});

describe("ProjectsCard", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders existing projects with remove button", () => {
    const store = makeStore();
    render(<ProjectsCard store={store} />);

    expect(screen.getByText("PROJECTS")).toBeInTheDocument();
    expect(screen.getByText("Project 1")).toBeInTheDocument();
    expect(screen.getByText("Project 2")).toBeInTheDocument();
    expect(screen.getByText(/Project ID: p1/)).toBeInTheDocument();
    expect(screen.getByText(/Project ID: p2/)).toBeInTheDocument();

    const removeBtns = screen.getAllByTitle("Remove");
    expect(removeBtns.length).toBe(2);
  });

  test("click remove confirms and calls store.removeProjectFromEncounter", () => {
    const store = makeStore();
    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);

    render(<ProjectsCard store={store} />);

    const removeBtn = screen.getAllByTitle("Remove")[0];
    fireEvent.click(removeBtn);

    expect(confirmSpy).toHaveBeenCalled();
    expect(store.removeProjectFromEncounter).toHaveBeenCalledWith("p1");

    confirmSpy.mockRestore();
  });

  test("click remove but cancel confirm does not call remove", () => {
    const store = makeStore();
    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(false);

    render(<ProjectsCard store={store} />);

    const removeBtn = screen.getAllByTitle("Remove")[0];
    fireEvent.click(removeBtn);

    expect(store.removeProjectFromEncounter).not.toHaveBeenCalled();

    confirmSpy.mockRestore();
  });

  test("react-select change calls store.setSelectedProjects with normalized data", () => {
    const store = makeStore();
    render(<ProjectsCard store={store} />);

    const select = screen.getByTestId("react-select");
    fireEvent.click(select);

    expect(store.setSelectedProjects).toHaveBeenCalledWith([
      { id: "p3", name: "Project 3" },
    ]);
  });

  test("ADD button calls addEncounterToProject and clear selection", () => {
    const store = makeStore({
      selectedProjects: [{ id: "p3", name: "Project 3" }],
    });
    render(<ProjectsCard store={store} />);

    const addBtn = screen.getByText("ADD_PROJECT");
    fireEvent.click(addBtn);

    expect(store.addEncounterToProject).toHaveBeenCalled();
    expect(store.setSelectedProjects).toHaveBeenCalledWith(null);
  });

  test("CANCEL button clears selection", () => {
    const store = makeStore({
      selectedProjects: [{ id: "p3", name: "Project 3" }],
    });
    render(<ProjectsCard store={store} />);

    const cancelBtn = screen.getByText("CANCEL");
    fireEvent.click(cancelBtn);

    expect(store.setSelectedProjects).toHaveBeenCalledWith(null);
  });

  test("when encounter has no projects, list is empty but select still appears", () => {
    const store = makeStore({
      encounterData: { projects: [] },
    });
    render(<ProjectsCard store={store} />);

    expect(screen.queryByText("Project 1")).not.toBeInTheDocument();
    expect(screen.getByText("SEARCH_PROJECT")).toBeInTheDocument();
    expect(screen.getByTestId("react-select")).toBeInTheDocument();
  });
});
