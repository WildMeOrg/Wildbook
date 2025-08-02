import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { LocationID } from "../../../pages/ReportsAndManagamentPages/LocationID";
import { renderWithProviders } from "../../../utils/utils";

// Mock store
const mockStore = {
  placeSection: {
    required: true,
    locationId: null,
    error: false,
  },
  setLocationError: jest.fn(),
  setLocationId: jest.fn(),
};

const locationData = [
  {
    id: "1",
    name: "Location 1",
    locationID: [],
    geospatialInfo: {},
  },
  {
    id: "2",
    name: "Location 2",
    locationID: [
      {
        id: "3",
        name: "SubLocation 1",
        locationID: [],
        geospatialInfo: {},
      },
    ],
    geospatialInfo: {},
  },
];

describe("LocationID Component", () => {
  test("renders without crashing", () => {
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );
    expect(screen.getByText(/PLACE_SECTION/i)).toBeInTheDocument();
  });

  test("renders required field indicator", () => {
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );
    expect(screen.getAllByText("*")).toHaveLength(2);
  });

  test("opens dropdown when clicked", async () => {
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );

    fireEvent.mouseDown(screen.getByText(/LOCATIONID_INSTRUCTION/i));
    await waitFor(() => {
      expect(screen.getByText("Location 1")).toBeInTheDocument();
    });
  });

  test("selecting a location updates store", async () => {
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );

    fireEvent.mouseDown(screen.getByText(/LOCATIONID_INSTRUCTION/i));
    await waitFor(() => {
      fireEvent.click(screen.getByText("Location 1"));
    });

    expect(mockStore.setLocationId).toHaveBeenCalledWith("1");
  });

  test("displays error message if location is required but not selected", async () => {
    mockStore.placeSection.error = true;
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );

    expect(screen.getAllByText(/LOCATION_ID_REQUIRED_WARNING/i)).toHaveLength(
      2,
    );
  });

  test("clears location selection when cancel is clicked", async () => {
    renderWithProviders(
      <LocationID store={mockStore} locationData={locationData} />,
    );
    fireEvent.mouseDown(screen.getByText(/LOCATIONID_INSTRUCTION/i));
    await waitFor(() => {
      fireEvent.click(screen.getByText("Location 1"));
    });
    fireEvent.click(screen.getByText(/CANCEL/i));
    expect(mockStore.setLocationId).toHaveBeenCalledWith(null);
  });
});
