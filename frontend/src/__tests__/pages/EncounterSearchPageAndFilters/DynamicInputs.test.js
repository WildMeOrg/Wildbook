import React from "react";
import { fireEvent, screen } from "@testing-library/react";
import DynamicInputs from "../../../components/Form/DynamicInputs";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("DynamicInputs Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const renderComponent = () =>
    renderWithProviders(<DynamicInputs store={mockStore} />);

  it("renders without crashing", () => {
    renderComponent();
    expect(
      screen.getByText("FILTER_ADD_OBSERVATION_SEARCH"),
    ).toBeInTheDocument();
  });

  it("adds a new input field when button is clicked", () => {
    renderComponent();
    const addButton = screen.getByRole("button");
    fireEvent.click(addButton);
    expect(screen.getAllByRole("textbox").length).toBe(2);
  });

  it("updates input field values correctly", () => {
    renderComponent();
    fireEvent.click(screen.getByRole("button"));
    const nameInput = screen.getAllByRole("textbox")[0];
    const valueInput = screen.getAllByRole("textbox")[1];

    fireEvent.change(nameInput, { target: { value: "testName" } });
    fireEvent.change(valueInput, { target: { value: "testValue" } });

    expect(nameInput.value).toBe("testName");
    expect(valueInput.value).toBe("testValue");
  });

  it("calls store.addFilter on blur when both fields are filled", () => {
    renderComponent();
    fireEvent.click(screen.getByRole("button"));
    const nameInput = screen.getAllByRole("textbox")[0];
    const valueInput = screen.getAllByRole("textbox")[1];

    fireEvent.change(nameInput, { target: { value: "testName" } });
    fireEvent.change(valueInput, { target: { value: "testValue" } });

    fireEvent.blur(valueInput);

    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "dynamicProperties.testName",
      "filter",
      { match: { "dynamicProperties.testName": "testValue" } },
      "dynamicProperties.testName",
    );
  });

  it("calls store.removeFilter on blur when value is empty", () => {
    mockStore.formFilters = [
      {
        filterId: "dynamicProperties.testName",
        filterKey: "dynamicProperties.testName",
        query: { match: { "dynamicProperties.testName": "testValue" } },
      },
    ];

    renderComponent();
    const valueInput = screen.getAllByRole("textbox")[1];
    fireEvent.change(valueInput, { target: { value: "" } });
    fireEvent.blur(valueInput);

    expect(mockStore.removeFilter).toHaveBeenCalledWith(
      "dynamicProperties.testName",
    );
  });
});
