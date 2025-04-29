import React from "react";
import { screen } from "@testing-library/react";
import MetadataFilter from "../../../components/filterFields/MetadataFilter";
import { renderWithProviders } from "../../../utils/utils";

// Mocks
jest.mock("../../../components/Form/FormGroupMultiSelect", () => {
  const MockFormGroupMultiSelect = (props) => (
    <div data-testid={`formgroup-${props.field}`}>{props.label}</div>
  );

  MockFormGroupMultiSelect.displayName = "MockFormGroupMultiSelect";

  return MockFormGroupMultiSelect;
});

jest.mock("../../../components/filterFields/SubmitterFilter", () => {
  const MockSubmitterFilter = () => (
    <div data-testid="submitter-filter">SubmitterFilter</div>
  );

  MockSubmitterFilter.displayName = "MockSubmitterFilter";

  return MockSubmitterFilter;
});

jest.mock("../../../components/Form/Description", () => {
  const MockDescription = ({ children }) => (
    <div data-testid="description">{children}</div>
  );

  MockDescription.displayName = "MockDescription";

  return MockDescription;
});

describe("MetadataFilter Component", () => {
  const mockData = {
    encounterState: ["Active", "Completed"],
    organizations: { org1: "Org One", org2: "Org Two" },
    projectsForUser: { proj1: "Project One", proj2: "Project Two" },
    users: [{ username: "user1" }, { username: "user2" }, {}],
  };

  const mockStore = {};

  it("renders the header and description", () => {
    renderWithProviders(<MetadataFilter data={mockData} store={mockStore} />);
    expect(screen.getByText("FILTER_METADATA")).toBeInTheDocument();
    expect(screen.getByTestId("description")).toBeInTheDocument();
  });

  it("renders SubmitterFilter", () => {
    renderWithProviders(<MetadataFilter data={mockData} store={mockStore} />);
    expect(screen.getByTestId("submitter-filter")).toBeInTheDocument();
  });

  it("renders all FormGroupMultiSelect components", () => {
    renderWithProviders(<MetadataFilter data={mockData} store={mockStore} />);

    expect(screen.getByTestId("formgroup-state")).toBeInTheDocument();
    expect(screen.getByTestId("formgroup-organizations")).toBeInTheDocument();
    expect(screen.getByTestId("formgroup-projects")).toBeInTheDocument();
    expect(
      screen.getByTestId("formgroup-assignedUsername"),
    ).toBeInTheDocument();
  });

  it("handles missing data gracefully", () => {
    renderWithProviders(<MetadataFilter data={{}} store={mockStore} />);

    expect(screen.getByTestId("formgroup-state")).toBeInTheDocument();
    expect(screen.getByTestId("formgroup-organizations")).toBeInTheDocument();
    expect(screen.getByTestId("formgroup-projects")).toBeInTheDocument();
    expect(
      screen.getByTestId("formgroup-assignedUsername"),
    ).toBeInTheDocument();
  });
});
