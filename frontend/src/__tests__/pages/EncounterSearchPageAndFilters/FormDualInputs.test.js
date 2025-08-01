import React from "react";
import { fireEvent, screen } from "@testing-library/react";
import FormDualInputs from "../../../components/Form/FormDualInputs";
import { renderWithProviders } from "../../../utils/utils";

describe("FormDualInputs Component", () => {
  let mockOnChange;

  beforeEach(() => {
    mockOnChange = jest.fn();
  });

  const renderComponent = () =>
    renderWithProviders(
      <FormDualInputs
        label="testLabel"
        label1="Name Placeholder"
        label2="Value Placeholder"
        onChange={mockOnChange}
      />,
    );

  it("renders input fields with correct placeholders", () => {
    renderComponent();
    const inputs = screen.getAllByRole("textbox");
    expect(inputs[0]).toHaveAttribute("placeholder", "Name Placeholder");
    expect(inputs[1]).toHaveAttribute("placeholder", "Value Placeholder");
  });

  it("updates name and value inputs correctly", () => {
    renderComponent();
    const nameInput = screen.getAllByRole("textbox")[0];
    const valueInput = screen.getAllByRole("textbox")[1];

    fireEvent.change(nameInput, { target: { value: "testName" } });
    fireEvent.change(valueInput, { target: { value: "testValue" } });

    expect(nameInput.value).toBe("testName");
    expect(valueInput.value).toBe("testValue");
  });

  it("calls onChange with correct filter data on input change", () => {
    renderComponent();
    const nameInput = screen.getAllByRole("textbox")[0];
    const valueInput = screen.getAllByRole("textbox")[1];

    fireEvent.change(nameInput, { target: { value: "testName" } });
    fireEvent.change(valueInput, { target: { value: "testValue" } });

    expect(mockOnChange).toHaveBeenCalledWith({
      filterId: "testLabel.testName",
      clause: "filter",
      query: {
        match: {
          "dynamicProperties.testName": "testValue",
        },
      },
      term: "match",
    });
  });
});
