import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import IndividualDateFilter from "../../../components/filterFields/IndividualDateFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("IndividualDateFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<IndividualDateFilter store={mockStore} />);
  };

  test("renders labels and descriptions correctly", () => {
    renderComponent();

    expect(screen.getByText(/FILTER_DATE_DESC/i)).toBeInTheDocument();
    expect(screen.getByText(/FILTER_BIRTH/i)).toBeInTheDocument();
    expect(screen.getByText(/FILTER_DEATH/i)).toBeInTheDocument();
  });

  test("handles birth date input correctly", () => {
    renderComponent();

    const birthDateInput = document.querySelectorAll(
      'input[type="date"].form-control',
    )[0];

    fireEvent.change(birthDateInput, { target: { value: "2024-03-01" } });

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "individualTimeOfBirth",
      "filter",
      {
        range: {
          individualTimeOfBirth: {
            gte: "2024-03-01T00:00:00.000Z",
            lte: "2024-03-01T23:59:59.000Z",
          },
        },
      },
      "Birth Date",
    );
  });

  test("handles death date input correctly", () => {
    renderComponent();

    const deathDateInput = document.querySelectorAll(
      'input[type="date"].form-control',
    )[1];
    fireEvent.change(deathDateInput, { target: { value: "2024-04-01" } });

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "individualTimeOfDeath",
      "filter",
      {
        range: {
          individualTimeOfDeath: {
            gte: "2024-04-01T00:00:00.000Z",
            lte: "2024-04-01T23:59:59.000Z",
          },
        },
      },
      "Death Date",
    );
  });
});
