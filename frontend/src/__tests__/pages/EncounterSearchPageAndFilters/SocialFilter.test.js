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

  test("handles 'AND' operator checkbox for Relationship Role correctly", () => {
    renderComponent();

    const relationshipRoleAndCheckbox = screen.getAllByRole("checkbox", {
      name: /USE_AND_OPERATOR/i,
    })[1];

    fireEvent.click(relationshipRoleAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).not.toHaveBeenCalled();

    fireEvent.click(relationshipRoleAndCheckbox);

    expect(mockStore.removeFilterByFilterKey).toHaveBeenCalledWith(
      "Relationship Role",
    );
  });
});
