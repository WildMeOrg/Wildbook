import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import SocialFilter from "../../../components/filterFields/SocialFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
  removeFilterByFilterKey: jest.fn(),
};

const mockData = {
  relationshipRole: ["Parent", "Sibling"],
  socialUnitRole: ["Family", "Group"],
};

describe("SocialFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<SocialFilter data={mockData} store={mockStore} />);
  };

  test("renders labels and descriptions correctly", () => {
    renderComponent();

    expect(screen.getByText("FILTER_SOCIAL")).toBeInTheDocument();
    expect(screen.getByText("FILTER_SOCIAL_DESC")).toBeInTheDocument();
    expect(screen.getByText("FILTER_GROUP_BEHAVIOR")).toBeInTheDocument();
    expect(screen.getByText("FILTER_GROUP_COMPOSITION")).toBeInTheDocument();
    expect(screen.getByText("FILTER_SOCIAL_UNIT")).toBeInTheDocument();
    expect(screen.getByText("FILTER_RELATIONSHIP_ROLE")).toBeInTheDocument();
  });

  test("handles 'AND' operator checkbox for Social Unit correctly", () => {
    renderComponent();

    const socialUnitAndCheckbox =
      screen.getAllByLabelText("USE_AND_OPERATOR")[0];

    fireEvent.click(socialUnitAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).not.toHaveBeenCalled();

    fireEvent.click(socialUnitAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).toHaveBeenCalledWith(
      "Social Group Unit",
    );
  });

  test("renders Social Unit options correctly after selecting AND operator", async () => {
    renderComponent();

    const socialUnitAndCheckbox =
      screen.getAllByLabelText("USE_AND_OPERATOR")[0];
    fireEvent.keyDown(socialUnitAndCheckbox);

    expect(await screen.findByText("Family")).toBeInTheDocument();
    expect(await screen.findByText("Group")).toBeInTheDocument();
  });

  test("handles 'AND' operator checkbox for Relationship Role correctly", () => {
    renderComponent();

    const relationshipRoleAndCheckbox =
      screen.getAllByLabelText("USE_AND_OPERATOR")[1];

    fireEvent.click(relationshipRoleAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).not.toHaveBeenCalled();

    fireEvent.click(relationshipRoleAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).toHaveBeenCalledWith(
      "Relationship Role",
    );
  });

  test("renders Relationship Role options correctly after selecting AND operator", async () => {
    renderComponent();

    const relationshipRoleAndCheckbox =
      screen.getAllByLabelText("USE_AND_OPERATOR")[1];
    fireEvent.keyDown(relationshipRoleAndCheckbox);

    expect(await screen.findByText("Parent")).toBeInTheDocument();
    expect(await screen.findByText("Sibling")).toBeInTheDocument();
  });

  test("matches snapshot", () => {
    const { asFragment } = renderWithProviders(
      <SocialFilter data={mockData} store={mockStore} />,
    );

    expect(asFragment()).toMatchSnapshot();
  });
});
