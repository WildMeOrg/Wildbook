import React from "react";
import { screen } from "@testing-library/react";
import IndividualEstimateFilter from "../../../components/filterFields/IndividualEstimateFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("IndividualEstimateFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<IndividualEstimateFilter store={mockStore} />);
  };

  test("renders main label and description correctly", () => {
    renderComponent();

    expect(
      screen.getByText(/FILTER_INDIVIDUAL_ESTIMATE_DESC/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/FILTER_BEST_ESTIMATE_INDIVIDUALS/),
    ).toBeInTheDocument();
  });

  test("renders all FormGroupText components correctly", () => {
    renderComponent();

    expect(
      screen.getByText(/FILTER_BEST_ESTIMATE_INDIVIDUALS/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/FILTER_MINIMUM_ESTIMATE_INDIVIDUALS/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/FILTER_MAXIMUM_ESTIMATE_INDIVIDUALS/),
    ).toBeInTheDocument();
  });

  test("matches snapshot", () => {
    const { asFragment } = renderWithProviders(
      <IndividualEstimateFilter store={mockStore} />,
    );

    expect(asFragment()).toMatchSnapshot();
  });

  test("calls store methods on interaction with FormGroupText components", () => {
    renderComponent();

    expect(mockStore.addFilter).not.toHaveBeenCalled();
    expect(mockStore.removeFilter).not.toHaveBeenCalled();
  });

  test("handles store state correctly", () => {
    mockStore.formFilters = [
      {
        filterId: "occurrenceBestGroupSizeEstimate",
        query: { match: { occurrenceBestGroupSizeEstimate: "10" } },
      },
    ];

    renderComponent();

    expect(
      screen.getByText(/FILTER_BEST_ESTIMATE_INDIVIDUALS/),
    ).toBeInTheDocument();
  });
});
