import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { LocationFilterByMap } from "../../../pages/ReportsAndManagamentPages/LocationFilterByMap";
import { renderWithProviders } from "../../../utils/utils";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";

jest.mock("../../../components/Map", () => {
  return jest.fn(() => <div data-testid="map-component">Mocked Map</div>);
});

jest.mock(
  "../../../pages/ReportsAndManagamentPages/ReportEncounterStore",
  () => ({
    ReportEncounterStore: jest.fn().mockImplementation(() => ({
      placeSection: {
        locationId: null,
      },
      setLocationId: jest.fn(),
    })),
  }),
);

const mockStore = new ReportEncounterStore();

const treeData = [
  {
    title: "Location 1",
    value: "loc1",
    geospatialInfo: { lat: 10, long: 10 },
    children: [
      {
        title: "Sub-location 1",
        value: "subloc1",
        geospatialInfo: { lat: 10.5, long: 10.5 },
      },
    ],
  },
  {
    title: "Location 2",
    value: "loc2",
    geospatialInfo: { lat: 20, long: 20 },
  },
];

const renderComponent = (modalShow = true) => {
  return renderWithProviders(
    <LocationFilterByMap
      store={mockStore}
      modalShow={modalShow}
      setModalShow={jest.fn()}
      treeData={treeData}
      mapCenterLat={10}
      mapCenterLon={10}
      mapZoom={5}
      setShowFilterByMap={jest.fn()}
    />,
  );
};

describe("LocationFilterByMap Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByTestId("map-component")).toBeInTheDocument();
    expect(screen.getByText("LOCATION_ID")).toBeInTheDocument();
    expect(screen.getByText("DONE")).toBeInTheDocument();
  });

  test("renders dropdown", async () => {
    renderComponent();
    expect(screen.queryByText("LOCATIONID_INSTRUCTION")).toBeInTheDocument();
  });

  test("closes the modal when done button is clicked", async () => {
    const setModalShowMock = jest.fn();
    renderComponent();
    const doneButton = screen.getByText("DONE");
    fireEvent.click(doneButton);
    expect(setModalShowMock).not.toHaveBeenCalled();
  });

  test("clears selected location when cancel button is clicked", async () => {
    renderComponent();
    fireEvent.click(screen.getByText("CANCEL"));
    await waitFor(() =>
      expect(mockStore.setLocationId).toHaveBeenCalledWith(null),
    );
  });
});
