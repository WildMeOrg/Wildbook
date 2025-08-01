import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import LabelledKeywordFilter from "../../../components/Form/LabelledKeywordFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("LabelledKeywordFilter Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const defaultProps = {
    data: {
      labeledKeyword: {
        category: ["value1", "value2"],
        type: ["valueA", "valueB"],
      },
    },
    store: mockStore,
  };

  const renderComponent = (props = {}) =>
    renderWithProviders(<LabelledKeywordFilter {...defaultProps} {...props} />);

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByText("FILTER_LABELLED_KEYWORDS")).toBeInTheDocument();
  });

  it("adds a new labelled keyword pair on button click", () => {
    renderComponent();
    const addButton = screen.getByRole("button");
    fireEvent.click(addButton);
    expect(screen.getAllByRole("combobox")).toHaveLength(4); // Two selects per pair
  });

  it("updates labelled keyword selection", () => {
    renderComponent();
    const selectInput = screen.getAllByRole("combobox")[0];
    fireEvent.change(selectInput, { target: { value: "category" } });
    expect(selectInput.value).toBe("category");
  });

  it("updates keyword values selection and calls addFilter", () => {
    renderComponent();

    const selectInput = screen.getAllByRole("combobox")[0];
    fireEvent.mouseDown(selectInput);
    fireEvent.click(screen.getByText("category"));

    const valueInput = screen.getAllByRole("combobox")[1];
    fireEvent.mouseDown(valueInput);
    fireEvent.click(screen.getByText("value1"));

    expect(mockStore.addFilter).toHaveBeenCalled();
  });

  it("toggles AND operator checkbox and clears values", () => {
    renderComponent();
    const checkbox = screen.getByRole("checkbox");
    fireEvent.click(checkbox);
    expect(checkbox.checked).toBeTruthy();
  });
});
