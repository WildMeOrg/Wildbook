import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { PlaceSection } from "../../../pages/ReportsAndManagamentPages/PlaceSection";
import useGetSiteSettings from "../../../models/useGetSiteSettings";
import { renderWithProviders } from "../../../utils/utils";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";

jest.mock("../../../models/useGetSiteSettings");
jest.mock("@googlemaps/js-api-loader", () => ({
  Loader: jest.fn().mockImplementation(() => ({
    load: jest.fn().mockResolvedValue({}),
  })),
}));

jest.mock(
  "../../../pages/ReportsAndManagamentPages/ReportEncounterStore",
  () => ({
    ReportEncounterStore: jest.fn().mockImplementation(() => ({
      lat: 51,
      lon: 7,
      setLat: jest.fn(),
      setLon: jest.fn(),
      placeSection: {
        required: true,
        locationId: "test-id",
      },
    })),
  }),
);

const mockStore = new ReportEncounterStore();

const renderComponent = () => {
  return renderWithProviders(<PlaceSection store={mockStore} />);
};
describe("PlaceSection Component", () => {
  beforeEach(() => {
    useGetSiteSettings.mockReturnValue({
      data: {
        mapCenterLat: 51,
        mapCenterLon: 7,
        mapZoom: 4,
        googleMapsKey: "test-key",
        locationData: [{ locationID: "test-id" }],
      },
    });
  });

  test("renders without crashing", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    expect(screen.getAllByText(/LOCATION_ID/i)).toHaveLength(2);
    expect(screen.getByText(/FILTER_GPS_COORDINATES/i)).toBeInTheDocument();
    expect(screen.getByText(/PLACE_SECTION/i)).toBeInTheDocument();
    expect(screen.getAllByPlaceholderText("##.##")).toHaveLength(2);
  });

  test("renders latitude and longitude input fields", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const latInput = screen.getAllByPlaceholderText("##.##")[0];
    const lonInput = screen.getAllByPlaceholderText("##.##")[1];
    expect(latInput).toBeInTheDocument();
    expect(lonInput).toBeInTheDocument();
  });

  test("updates store on latitude input change", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const latInput = screen.getAllByPlaceholderText("##.##")[0];
    fireEvent.change(latInput, { target: { value: "45" } });
    expect(mockStore.setLat).toHaveBeenCalledWith("45");
  });

  test("updates store on longitude input change", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const lonInput = screen.getAllByPlaceholderText("##.##")[1];
    fireEvent.change(lonInput, { target: { value: "90" } });
    expect(mockStore.setLon).toHaveBeenCalledWith("90");
  });

  test("shows alert when latitude is out of range", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const latInput = screen.getAllByPlaceholderText("##.##")[0];
    fireEvent.change(latInput, { target: { value: "100" } });
    expect(screen.getByText(/INVALID_LAT/i)).toBeInTheDocument();
  });

  test("shows alert when longitude is out of range", () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const lonInput = screen.getAllByPlaceholderText("##.##")[1];
    fireEvent.change(lonInput, { target: { value: "200" } });
    expect(screen.getByText(/INVALID_LONG/i)).toBeInTheDocument();
  });

  test("clears latitude alert when valid latitude is entered", async () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const latInput = screen.getAllByPlaceholderText("##.##")[0];
    fireEvent.change(latInput, { target: { value: "100" } });
    expect(screen.getByText(/INVALID_LAT/i)).toBeInTheDocument();
    fireEvent.change(latInput, { target: { value: "45" } });
    await waitFor(() => expect(screen.queryByText(/INVALID_LAT/i)).toBeNull());
  });

  test("clears longitude alert when valid longitude is entered", async () => {
    renderComponent(<PlaceSection store={mockStore} />);
    const lonInput = screen.getAllByPlaceholderText("##.##")[1];
    fireEvent.change(lonInput, { target: { value: "200" } });
    expect(screen.getByText(/INVALID_LONG/i)).toBeInTheDocument();
    fireEvent.change(lonInput, { target: { value: "90" } });
    await waitFor(() => expect(screen.queryByText(/INVALID_LONG/i)).toBeNull());
  });
});
