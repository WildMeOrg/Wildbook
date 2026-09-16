/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

jest.mock("../../../pages/Encounter/TrackingReview", () => ({
  TrackingReview: () => (
    <div data-testid="tracking-review">tracking-review</div>
  ),
}));
jest.mock("../../../pages/Encounter/TrackingEdit", () => ({
  TrackingEdit: () => <div data-testid="tracking-edit">tracking-edit</div>,
}));
jest.mock("../../../pages/Encounter/ProjectsCard", () => ({
  ProjectsCard: () => <div data-testid="projects-card">projects-card</div>,
}));
jest.mock("../../../pages/Encounter/MeasurementsEdit", () => ({
  MeasurementsEdit: () => (
    <div data-testid="measurements-edit">measurements-edit</div>
  ),
}));
jest.mock("../../../pages/Encounter/MeasurementsReview", () => ({
  MeasurementsReview: () => (
    <div data-testid="measurements-review">measurements-review</div>
  ),
}));

jest.mock("../../../components/icons/TrackingIcon", () => (props) => (
  <div data-testid="tracking-icon" {...props}>
    tracking-icon
  </div>
));
jest.mock("../../../components/icons/MeasurementsIcon", () => (props) => (
  <div data-testid="measurements-icon" {...props}>
    measurements-icon
  </div>
));

jest.mock("../../../components/CardWithEditButton", () => (props) => (
  <div data-testid={`card-edit-${props.title}`}>
    <div>{props.title}</div>
    <button onClick={props.onClick} data-testid={`edit-btn-${props.title}`}>
      edit
    </button>
    <div>{props.content}</div>
  </div>
));

jest.mock("../../../components/CardWithSaveAndCancelButtons", () => (props) => (
  <div data-testid={`card-save-${props.title}`}>
    <div>{props.title}</div>
    <div>{props.content}</div>
    <button
      onClick={props.onSave}
      disabled={props.disabled}
      data-testid={`save-btn-${props.title}`}
    >
      save
    </button>
    <button onClick={props.onCancel} data-testid={`cancel-btn-${props.title}`}>
      cancel
    </button>
  </div>
));

import { MoreDetails } from "../../../pages/Encounter/MoreDetails";

const makeStore = (overrides = {}) => ({
  measurementsAndTrackingSection: true,
  biologicalSamplesSection: false,
  projectsSection: false,

  editTracking: false,
  setEditTracking: jest.fn(),

  editMeasurements: false,
  setEditMeasurements: jest.fn(),
  patchMeasurements: jest.fn(),
  resetMeasurementValues: jest.fn(),
  refreshEncounterData: jest.fn(),

  patchTracking: jest.fn(),

  setMeasurementsAndTrackingSection: jest.fn(),
  setBiologicalSamplesSection: jest.fn(),
  setProjectsSection: jest.fn(),

  measurementValues: [1],
  errors: {
    getFieldError: jest.fn(() => []),
  },

  encounterData: {
    id: "E-123",
  },

  ...overrides,
});

describe("MoreDetails", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders measurements and tracking section by default", () => {
    const store = makeStore();
    render(<MoreDetails store={store} />);

    expect(screen.getByTestId("card-edit-TRACKING")).toBeInTheDocument();
    expect(screen.getByTestId("tracking-review")).toBeInTheDocument();
    expect(screen.getByTestId("card-edit-MEASUREMENTS")).toBeInTheDocument();
    expect(screen.getByTestId("measurements-review")).toBeInTheDocument();
  });

  test("click tracking edit button switches to edit card and calls store.setEditTracking(true)", () => {
    const store = makeStore();
    render(<MoreDetails store={store} />);

    const editBtn = screen.getByTestId("edit-btn-TRACKING");
    fireEvent.click(editBtn);

    expect(store.setEditTracking).toHaveBeenCalledWith(true);
  });

  test("when editTracking=true, shows save/cancel card and save calls patchTracking", () => {
    const store = makeStore({ editTracking: true });
    render(<MoreDetails store={store} />);

    expect(screen.getByTestId("card-save-TRACKING")).toBeInTheDocument();
    const saveBtn = screen.getByTestId("save-btn-TRACKING");
    fireEvent.click(saveBtn);

    expect(store.patchTracking).toHaveBeenCalled();
    expect(store.setEditTracking).toHaveBeenCalledWith(false);
  });

  test("when editMeasurements=true, save calls patchMeasurements, refreshEncounterData and resetMeasurementValues", () => {
    const store = makeStore({ editMeasurements: true });
    render(<MoreDetails store={store} />);

    const saveBtn = screen.getByTestId("save-btn-MEASUREMENTS");
    fireEvent.click(saveBtn);

    expect(store.patchMeasurements).toHaveBeenCalled();
    expect(store.refreshEncounterData).toHaveBeenCalled();
    expect(store.setEditMeasurements).toHaveBeenCalledWith(false);
    expect(store.resetMeasurementValues).toHaveBeenCalled();
  });

  test("click projects tab switches to projects section", () => {
    const store = makeStore();
    render(<MoreDetails store={store} />);

    const projectsTab = screen.getByText("PROJECTS");
    fireEvent.click(projectsTab);

    expect(store.setProjectsSection).toHaveBeenCalledWith(true);
    expect(store.setMeasurementsAndTrackingSection).toHaveBeenCalledWith(false);
    expect(store.setBiologicalSamplesSection).toHaveBeenCalledWith(false);
  });

  test("when projectsSection=true, renders ProjectsCard only", () => {
    const store = makeStore({
      measurementsAndTrackingSection: false,
      projectsSection: true,
    });
    render(<MoreDetails store={store} />);

    expect(screen.getByTestId("projects-card")).toBeInTheDocument();
    expect(screen.queryByTestId("card-edit-TRACKING")).not.toBeInTheDocument();
  });

  test("click biological samples tab opens window", () => {
    const store = makeStore();
    window.open = jest.fn();

    render(<MoreDetails store={store} />);

    const bioTab = screen.getByText("BIOLOGICAL_SAMPLES");
    fireEvent.click(bioTab);

    expect(store.setMeasurementsAndTrackingSection).toHaveBeenCalledWith(true);
    expect(store.setProjectsSection).toHaveBeenCalledWith(false);
    expect(window.open).toHaveBeenCalledWith(
      "/encounters/biologicalSamples.jsp?number=E-123",
      "_blank",
    );
  });

  test("measurements save is disabled when measurementValues empty or has errors", () => {
    const store = makeStore({
      editMeasurements: true,
      measurementValues: [],
      errors: {
        getFieldError: jest.fn(() => ["some error"]),
      },
    });
    render(<MoreDetails store={store} />);

    const saveBtn = screen.getByTestId("save-btn-MEASUREMENTS");
    expect(saveBtn).toBeDisabled();
  });
});
