import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { ReportEncounterSpeciesSection } from "../../../pages/ReportsAndManagamentPages/SpeciesSection";
import useGetSiteSettings from "../../../models/useGetSiteSettings";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../models/useGetSiteSettings", () => jest.fn());

const mockStore = {
  speciesSection: {
    required: true,
    value: "",
    error: false,
    setSpeciesSectionValue: jest.fn(),
  },
  setSpeciesSectionValue: jest.fn(),
};

describe("ReportEncounterSpeciesSection Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders correctly with required fields", () => {
    useGetSiteSettings.mockReturnValue({
      data: {
        siteTaxonomies: [
          { scientificName: "Panthera leo" },
          { scientificName: "Canis lupus" },
        ],
      },
    });

    renderWithProviders(<ReportEncounterSpeciesSection store={mockStore} />);

    expect(screen.getAllByText("SPECIES")).toHaveLength(2);
    expect(
      screen.getByText(/SPECIES_REQUIRED_IA_WARNING/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toBeInTheDocument();
    expect(screen.getByText(/Unknown/)).toBeInTheDocument();
  });

  it("displays the correct options in the select dropdown", () => {
    useGetSiteSettings.mockReturnValue({
      data: {
        siteTaxonomies: [
          { scientificName: "Panthera leo" },
          { scientificName: "Canis lupus" },
        ],
      },
    });

    renderWithProviders(<ReportEncounterSpeciesSection store={mockStore} />);

    const selectElement = screen.getByRole("combobox");
    expect(selectElement).toBeInTheDocument();
    expect(screen.getByText(/Panthera leo/i)).toBeInTheDocument();
    expect(screen.getByText(/Canis lupus/i)).toBeInTheDocument();
    expect(screen.getByText(/Unknown/i)).toBeInTheDocument();
  });

  it("calls setSpeciesSectionValue when an option is selected", () => {
    useGetSiteSettings.mockReturnValue({
      data: {
        siteTaxonomies: [
          { scientificName: "Panthera leo" },
          { scientificName: "Canis lupus" },
        ],
      },
    });

    renderWithProviders(<ReportEncounterSpeciesSection store={mockStore} />);

    const selectElement = screen.getByRole("combobox");
    fireEvent.change(selectElement, { target: { value: "Canis lupus" } });
    screen.getByRole("option", { name: /Canis lupus/i });
    expect(mockStore.setSpeciesSectionValue).toHaveBeenCalledWith(
      "Canis lupus",
    );
  });

  it("displays error message when species selection is required but not provided", () => {
    useGetSiteSettings.mockReturnValue({
      data: { siteTaxonomies: [] },
    });

    mockStore.speciesSection.error = true;

    renderWithProviders(<ReportEncounterSpeciesSection store={mockStore} />);

    expect(screen.getByText(/EMPTY_REQUIRED_WARNING/i)).toBeInTheDocument();
  });

  it("allows selecting 'Unknown' as an option", () => {
    useGetSiteSettings.mockReturnValue({
      data: {
        siteTaxonomies: [{ scientificName: "Panthera leo" }],
      },
    });

    renderWithProviders(<ReportEncounterSpeciesSection store={mockStore} />);

    const selectElement = screen.getByRole("combobox");
    fireEvent.change(selectElement, { target: { value: "unknown" } });

    expect(mockStore.setSpeciesSectionValue).toHaveBeenCalledWith("unknown");
  });
});
