import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import DateFilter from "../../../components/filterFields/DateFilter";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("antd", () => ({
  DatePicker: ({ onChange, value, className = "" }) => (
    <input
      type="date"
      className={`form-control ${className}`.trim()}
      value={
        value && typeof value.format === "function"
          ? value.format("YYYY-MM-DD")
          : value || ""
      }
      onChange={(e) => {
        const val = e.target.value;
        onChange(val ? { format: () => val } : null);
      }}
    />
  ),
}));

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

const mockData = {
  verbatimEventDate: ["2024-01-01", "2024-01-02"],
};

describe("DateFilter Component", () => {
  beforeEach(() => {
    mockStore.formFilters = [];
    jest.clearAllMocks();
  });

  const renderComponent = () => {
    renderWithProviders(<DateFilter data={mockData} store={mockStore} />);
  };

  test("renders all labels correctly", () => {
    renderComponent();

    expect(screen.getAllByText(/FILTER_FROM/i)).toHaveLength(2);
    expect(screen.getAllByText(/FILTER_TO/i)).toHaveLength(2);
    expect(
      screen.getByText(/FILTER_ENCOUNTER_SUBMISSION_DATE/i),
    ).toBeInTheDocument();
  });

  test("handles startDate and endDate inputs correctly", () => {
    renderComponent();

    const dateInputs = document.querySelectorAll(
      'input[type="date"].form-control',
    );
    const startDateInput = dateInputs[0];
    const endDateInput = dateInputs[1];

    fireEvent.change(startDateInput, { target: { value: "2024-01-10" } });
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "date",
      "filter",
      { range: { date: { gte: "2024-01-10T00:00:00Z" } } },
      "Sighting Date",
    );

    fireEvent.change(endDateInput, { target: { value: "2024-01-20" } });
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "date",
      "filter",
      {
        range: {
          date: { gte: "2024-01-10T00:00:00Z", lte: "2024-01-20T23:59:59Z" },
        },
      },
      "Sighting Date",
    );
  });

  test("handles submissionStartDate and submissionEndDate correctly", () => {
    renderComponent();

    const dateInputs = document.querySelectorAll(
      'input[type="date"].form-control',
    );
    const submissionStartInput = dateInputs[2];
    const submissionEndInput = dateInputs[3];

    fireEvent.change(submissionStartInput, { target: { value: "2024-02-01" } });
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "dateSubmitted",
      "filter",
      { range: { dateSubmitted: { gte: "2024-02-01T00:00:00Z" } } },
      "Date Submitted",
    );

    fireEvent.change(submissionEndInput, { target: { value: "2024-02-05" } });
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "dateSubmitted",
      "filter",
      {
        range: {
          dateSubmitted: {
            gte: "2024-02-01T00:00:00Z",
            lte: "2024-02-05T23:59:59Z",
          },
        },
      },
      "Date Submitted",
    );
  });

  test("removes filters when input fields are cleared", () => {
    mockStore.formFilters = [
      {
        filterId: "date",
        query: { range: { date: { gte: "2024-01-01T00:00:00Z" } } },
      },
    ];

    renderComponent();

    const dateInputs = document.querySelectorAll(
      'input[type="date"].form-control',
    );
    const startDateInput = dateInputs[0];
    fireEvent.change(startDateInput, { target: { value: "123" } });
    fireEvent.change(startDateInput, { target: { value: "" } });

    expect(mockStore.removeFilter).toHaveBeenCalledWith("date");
  });
});
