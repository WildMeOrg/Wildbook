import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import IdentityFilter from "../../../components/filterFields/IdentityFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("IdentityFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<IdentityFilter store={mockStore} />);
  };

  test("renders labels and placeholders correctly", () => {
    renderComponent();

    expect(screen.getByText("FILTER_IDENTITY")).toBeInTheDocument();
    expect(screen.getByText(/FILTER_SIGHTED_AT_LEAST/)).toBeInTheDocument();
    expect(screen.getByText(/FILTER_TIMES/)).toBeInTheDocument();
  });

  test("handles numeric input correctly", () => {
    renderComponent();

    const numericInput = document.querySelectorAll(
      'input[type="text"].form-control',
    )[0];

    fireEvent.change(numericInput, { target: { value: "5" } });

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "occurrenceIndividualCount",
      "filter",
      { match: { occurrenceIndividualCount: "5" } },
      "Number of Reported Marked Individuals",
    );

    fireEvent.change(numericInput, { target: { value: "" } });

    expect(mockStore.removeFilter).toHaveBeenCalledWith(
      "occurrenceIndividualCount",
    );
  });

  test("handles checkbox correctly", () => {
    renderComponent();

    const checkbox = screen.getByLabelText("FILTER_NO_INDIVIDUAL_ID");

    fireEvent.click(checkbox);

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "individualId",
      "must_not",
      { exists: { field: "individualId" } },
      "Include only encounters with no assigned Individual ID",
    );
  });

  test("renders text filter fields", () => {
    renderComponent();

    expect(
      screen.getByText(/FILTER_NUMBER_REPORTED_MARKED_INDIVIDUALS/),
    ).toBeInTheDocument();
    expect(screen.getByText(/FILTER_ALTERNATIVE_ID/)).toBeInTheDocument();
    expect(
      screen.getByText(/FILTER_INDIVIDUAL_NAME_DESC/i),
    ).toBeInTheDocument();
  });
});
