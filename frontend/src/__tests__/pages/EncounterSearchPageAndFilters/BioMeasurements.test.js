import React from "react";
import { screen } from "@testing-library/react";
import FormMeasurements from "../../../components/Form/BioMeasurements";
import userEvent from "@testing-library/user-event";
import { renderWithProviders } from "../../../utils/utils";

describe("FormMeasurements", () => {
  let store;

  beforeEach(() => {
    store = {
      formFilters: [
        {
          filterId: "filter1.height",
          query: { range: { "filter1.height": { gte: 150 } } },
        },
      ],
      addFilter: jest.fn(),
      removeFilter: jest.fn(),
    };
  });

  const renderComponent = (props) => {
    return renderWithProviders(<FormMeasurements {...props} store={store} />);
  };

  test("renders title", () => {
    renderComponent({ data: ["height", "weight"], filterId: "filter1" });
    expect(
      screen.getByText("FILTER_BIOLOGICAL_MEASUREMENTS"),
    ).toBeInTheDocument();
  });

  test("renders measurement fields correctly", () => {
    renderComponent({ data: ["height", "weight"], filterId: "filter1" });
    expect(screen.getByText("Height")).toBeInTheDocument();
    expect(screen.getByText("Weight")).toBeInTheDocument();
  });

  test("changes operator selection", async () => {
    renderComponent({ data: ["height"], filterId: "filter1" });
    const select = screen.getByLabelText("Select operator");
    await userEvent.selectOptions(select, "lte");
    expect(select.value).toBe("lte");
  });

  test("updates input value and triggers filter update", async () => {
    renderComponent({ data: ["height"], filterId: "filter1" });
    const input = screen.getByPlaceholderText("###");
    expect(input).toBeInTheDocument();
    expect(input).toHaveAttribute("type", "number");
    expect(input).toHaveClass("form-control");
    userEvent.clear(input);
    await userEvent.type(input, "170");
    expect(input.value).toBe("170");
  });

  test("pre-fills input fields when store has existing filters", () => {
    renderComponent({ data: ["height", "weight"], filterId: "filter1" });
    expect(screen.getByDisplayValue("150")).toBeInTheDocument();
  });

  test("removes filter when input value is cleared", async () => {
    renderComponent({ data: ["height"], filterId: "filter1" });
    const input = screen.getByPlaceholderText("###");
    userEvent.clear(input);
    await userEvent.type(input, "170");
    await userEvent.clear(input);
    expect(store.removeFilter).toHaveBeenCalled();
  });

  test("does not update store when input is empty", async () => {
    renderComponent({ data: ["height"], filterId: "filter1" });
    const input = screen.getByPlaceholderText("###");
    await userEvent.clear(input);
    expect(store.addFilter).not.toHaveBeenCalled();
  });

  test("handles undefined error scenarios gracefully", () => {
    renderComponent({ data: ["height"], filterId: "filter1" });
    expect(() => {
      store.removeFilter(undefined);
    }).not.toThrow();
  });
});
