import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import FormMeasurements from "../../../components/Form/FormMeasurements";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("FormMeasurements Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const defaultProps = {
    data: ["height", "weight"],
    field: "testField",
    filterId: "testFilterId",
    store: mockStore,
  };

  const renderComponent = (props = {}) =>
    renderWithProviders(<FormMeasurements {...defaultProps} {...props} />);

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByText("Height")).toBeInTheDocument();
    expect(screen.getByText("Weight")).toBeInTheDocument();
  });

  it("displays input fields for each data item", () => {
    renderComponent();
    expect(screen.getAllByPlaceholderText("###")).toHaveLength(2);
  });

  it("updates input value and calls addFilter", () => {
    renderComponent();
    const input = screen.getAllByPlaceholderText("###")[0];
    fireEvent.change(input, { target: { value: "180" } });

    expect(input.value).toBe("180");
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "testFilterId.height",
      "nested",
      expect.objectContaining({
        bool: expect.objectContaining({
          filter: expect.arrayContaining([
            expect.objectContaining({ match: { "testField.type": "height" } }),
          ]),
        }),
      }),
      "testFilterId.height",
      "testField",
    );
  });

  it("removes filter when input is cleared", () => {
    renderComponent();
    const input = screen.getAllByPlaceholderText("###")[0];
    fireEvent.change(input, { target: { value: "111" } });
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.blur(input);

    expect(mockStore.removeFilter).toHaveBeenCalledWith("testFilterId.height");
  });

  it("renders correct number of input fields when data changes", () => {
    const { rerender } = renderComponent();
    rerender(
      <FormMeasurements {...defaultProps} data={["height", "weight", "age"]} />,
    );
    expect(screen.getAllByPlaceholderText("###")).toHaveLength(3);
  });

  it("does not call addFilter when input value is unchanged", () => {
    renderComponent();
    const input = screen.getAllByPlaceholderText("###")[0];
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.change(input, { target: { value: "" } });
    expect(mockStore.addFilter).not.toHaveBeenCalled();
  });

  it("correctly initializes input values based on store filters", () => {
    mockStore.formFilters = [
      {
        filterId: "testFilterId.height",
        query: {
          bool: {
            filter: [{ range: { "testField.value": { gte: 175 } } }],
          },
        },
      },
    ];

    renderComponent();
    const input = screen.getAllByPlaceholderText("###")[0];
    expect(input.value).toBe("175");
  });
});
