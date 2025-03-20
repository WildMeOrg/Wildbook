import React from "react";
import { screen } from "@testing-library/react";
import FormGroupMultiSelect from "../../../components/Form/FormGroupMultiSelect";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/MultiSelect", () => () => {
  const mockComponent = () => <div data-testid="multi-select"></div>;
  mockComponent.displayName = "MultiSelect";
  return mockComponent;
});

describe("FormGroupMultiSelect Component", () => {
  const defaultProps = {
    label: "testLabel",
    options: [{ value: "1", label: "Option 1" }],
    filterKey: "testFilterKey",
    store: {},
  };

  const renderComponent = (props = {}) =>
    renderWithProviders(<FormGroupMultiSelect {...defaultProps} {...props} />);

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByText("testLabel_DESC")).toBeInTheDocument();
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

  it("renders MultiSelect component", () => {
    renderComponent();
    expect(screen.getByTestId("multi-select")).toBeInTheDocument();
  });
});
