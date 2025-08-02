import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import FormGroupText from "../../../components/Form/FormGroupText";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

describe("FormGroupText Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const defaultProps = {
    label: "testLabel",
    filterId: "testFilterId",
    field: "testField",
    term: "match",
    filterKey: "testFilterKey",
    store: mockStore,
  };

  const renderComponent = (props = {}) =>
    renderWithProviders(<FormGroupText {...defaultProps} {...props} />);

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByRole("textbox")).toBeInTheDocument();
  });

  it("renders label correctly", () => {
    renderComponent();
    expect(screen.getByText("testLabel")).toBeInTheDocument();
  });

  it("hides label when noLabel is true", () => {
    renderComponent({ noLabel: true });
    expect(screen.queryByText("testLabel")).not.toBeInTheDocument();
  });

  it("renders description correctly", () => {
    renderComponent();
    expect(screen.getByText(/testLabel_DESC/i)).toBeInTheDocument();
  });

  it("hides description when noDesc is true", () => {
    renderComponent({ noDesc: true });
    expect(screen.queryByText(/testLabel_DESC/i)).not.toBeInTheDocument();
  });

  it("updates input field and calls addFilter", () => {
    renderComponent();
    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "newValue" } });
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "testField",
      "filter",
      { match: { testField: "newValue" } },
      "testFilterKey",
    );
  });

  it("removes filter when input is cleared", () => {
    renderComponent();
    const input = screen.getByRole("textbox");
    fireEvent.change(input, { target: { value: "123" } });
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.blur(input);
    expect(mockStore.removeFilter).toHaveBeenCalledWith("testFilterId");
  });
});
