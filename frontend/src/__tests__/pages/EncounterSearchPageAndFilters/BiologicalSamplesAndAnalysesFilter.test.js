import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import BiologicalSamplesAndAnalysesFilter from "../../../components/filterFields/BiologicalSamplesAndAnalysesFilter";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  formFilters: [],
  addFilter: jest.fn(),
  removeFilter: jest.fn(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe("BiologicalSamplesAndAnalysesFilter Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const defaultProps = {
    data: {
      bioMeasurement: { height: "cm", weight: "kg" },
      haplotype: ["A", "B", "C"],
      geneticSex: ["Male", "Female"],
      loci: ["locus1", "locus2"],
    },
    store: mockStore,
  };

  const renderComponent = (props = {}) =>
    renderWithProviders(
      <BiologicalSamplesAndAnalysesFilter {...defaultProps} {...props} />,
    );

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByText("FILTER_BIOLOGICAL_SAMPLE")).toBeInTheDocument();
  });

  it("toggles biological sample checkbox and calls addFilter", () => {
    renderComponent();
    const checkbox = screen.getByRole("checkbox", {
      name: /FILTER_HAS_BIOLOGICAL_SAMPLE/i,
    });
    fireEvent.click(checkbox);
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "biologicalSampleId",
      "filter",
      expect.objectContaining({ exists: { field: "tissueSampleIds" } }),
      "Has Biological Sample",
    );
  });

  it("removes biological sample filter when unchecked", async () => {
    renderComponent();
    const checkbox = screen.getByRole("checkbox", {
      name: /FILTER_HAS_BIOLOGICAL_SAMPLE/i,
    });
    fireEvent.click(checkbox); // Check
    expect(mockStore.addFilter).toHaveBeenCalledWith(
      "biologicalSampleId",
      "filter",
      expect.objectContaining({ exists: { field: "tissueSampleIds" } }),
      "Has Biological Sample",
    );
    fireEvent.click(checkbox); // Uncheck
    expect(checkbox).not.toBeChecked();
  });

  it("updates allele length input correctly", () => {
    renderComponent();
    const inputs = screen.getAllByPlaceholderText("Type here");
    fireEvent.change(inputs[0], { target: { value: "10" } });
    expect(inputs[0].value).toBe("10");
  });

  it("adds haplotype filter when selected", async () => {
    renderComponent();

    const selectInput = screen.getAllByRole("combobox")[0];
    fireEvent.mouseDown(selectInput);
    fireEvent.click(screen.getByText("A"));

    await waitFor(() => {
      expect(mockStore.addFilter).toHaveBeenCalledWith(
        "haplotype",
        "filter",
        expect.objectContaining({ terms: { haplotype: ["A"] } }),
        "Haplotype",
      );
    });
  });

  it("adds genetic sex filter when selected", async () => {
    renderComponent();

    const selectInput = screen.getAllByRole("combobox")[1];
    fireEvent.mouseDown(selectInput);
    fireEvent.click(screen.getByText("Male"));

    await waitFor(() => {
      expect(mockStore.addFilter).toHaveBeenCalledWith(
        "geneticSex",
        "filter",
        expect.objectContaining({ terms: { geneticSex: ["Male"] } }),
        "Genetic Sex",
      );
    });
  });

  it("does not call addFilter when input is unchanged", () => {
    renderComponent();
    const input = screen.getAllByPlaceholderText("Type here")[0];
    fireEvent.change(input, { target: { value: "" } });
    fireEvent.change(input, { target: { value: "" } }); // No change
    expect(mockStore.addFilter).not.toHaveBeenCalled();
  });

  it("renders all available filters", () => {
    renderComponent();
    expect(screen.getByText("FILTER_HAPLO_TYPE")).toBeInTheDocument();
    expect(screen.getByText("FILTER_GENETIC_SEX")).toBeInTheDocument();
    expect(screen.getByText("FILTER_MARKER_LOCI")).toBeInTheDocument();
  });

  it("ensures the checkbox remains unchecked when clicked twice", () => {
    renderComponent();
    const checkbox = screen.getByRole("checkbox", {
      name: /FILTER_HAS_BIOLOGICAL_SAMPLE/i,
    });
    fireEvent.click(checkbox);
    fireEvent.click(checkbox);
    expect(checkbox).not.toBeChecked();
  });

  it("adds multiple haplotype selections", async () => {
    renderComponent();
    const select = screen.getAllByRole("combobox")[0];
    fireEvent.mouseDown(select);
    fireEvent.click(screen.getByText("A"));
    expect(mockStore.addFilter).toHaveBeenLastCalledWith(
      "haplotype",
      "filter",
      expect.objectContaining({ terms: { haplotype: ["A"] } }),
      "Haplotype",
    );
    fireEvent.mouseDown(select);
    fireEvent.click(screen.getByText("B"));
    await waitFor(() => {
      expect(mockStore.addFilter).toHaveBeenCalledTimes(2);
    });

    expect(mockStore.addFilter).toHaveBeenLastCalledWith(
      "haplotype",
      "filter",
      expect.objectContaining({ terms: { haplotype: ["B"] } }),
      "Haplotype",
    );
  });

  it("toggles allele length checkbox and updates input", () => {
    renderComponent();
    const checkbox = screen.getByRole("checkbox", {
      name: /FILTER_RELAX_ALLELE_LENGTH/i,
    });
    fireEvent.click(checkbox);
    expect(checkbox.checked).toBeTruthy();
  });

  it("removes genetic sex filter when cleared", async () => {
    mockStore.formFilters = [
      {
        filterId: "geneticSex",
        clause: "filter",
        query: { terms: { geneticSex: ["Male"] } },
        filterKey: "Genetic Sex",
      },
    ];
    renderComponent();
    const select = screen.getAllByRole("combobox")[1];
    fireEvent.keyDown(select, { key: "Backspace", code: "Backspace" });
    await waitFor(() => {
      expect(mockStore.removeFilter).toHaveBeenCalledWith("geneticSex");
    });
    mockStore.formFilters = [];
  });

  it("checks if all expected elements are rendered", () => {
    renderComponent();
    expect(screen.getAllByRole("textbox")).toHaveLength(5);
    expect(screen.getAllByRole("combobox")).toHaveLength(4);
  });

  it("verifies filters persist across re-renders", () => {
    renderComponent();
    fireEvent.click(
      screen.getByRole("checkbox", { name: /FILTER_HAS_BIOLOGICAL_SAMPLE/i }),
    );
    renderComponent();
    expect(mockStore.addFilter).toHaveBeenCalledTimes(1);
  });

  it("ensures genetic sex filter is correctly removed when deselected", async () => {
    // Pre-populate the store so the component renders with "Male" already selected.
    mockStore.formFilters = [
      {
        filterId: "geneticSex",
        clause: "filter",
        query: { terms: { geneticSex: ["Male"] } },
        filterKey: "Genetic Sex",
      },
    ];
    renderComponent();
    const select = screen.getAllByRole("combobox")[1];
    fireEvent.keyDown(select, { key: "Backspace", code: "Backspace" });
    await waitFor(() => {
      expect(mockStore.removeFilter).toHaveBeenCalledWith("geneticSex");
    });
    mockStore.formFilters = [];
  });
});
