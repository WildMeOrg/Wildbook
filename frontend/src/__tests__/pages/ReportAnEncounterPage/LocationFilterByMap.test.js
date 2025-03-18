import React from "react";
import { screen, fireEvent, waitFor, act } from "@testing-library/react";
import { LocationFilterByMap } from "../../../pages/ReportsAndManagamentPages/LocationFilterByMap";
import { renderWithProviders } from "../../../utils/utils";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";

jest.mock("../../../components/Map", () => {
  return jest.fn(() => <div data-testid="map-component">Mocked Map</div>);
});

jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore", () => ({
  ReportEncounterStore: jest.fn().mockImplementation(() => ({
    placeSection: {
      locationId: null,
      setLocationId: jest.fn(),
    },
  })),
}));

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
  return renderWithProviders(<LocationFilterByMap
    store={mockStore}
    modalShow={modalShow}
    setModalShow={jest.fn()}
    treeData={treeData}
    mapCenterLat={10}
    mapCenterLon={10}
    mapZoom={5}
    setShowFilterByMap={jest.fn()}
  />);
};

describe("LocationFilterByMap Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByTestId("map-component")).toBeInTheDocument();
    expect(screen.getByText("LOCATION_ID")).toBeInTheDocument();
  });

  test("selects a location from the dropdown", async () => {
    renderComponent();
    const dropdown = screen.getByRole("combobox");
    await act(async () => {
      fireEvent.mouseDown(dropdown);
    });

    await waitFor(() => {
      const options = screen.getAllByRole("treeitem"); 
      expect(options.length).toBeGreaterThan(0); 
    });

    await waitFor(() =>
      expect(screen.getByText("Location 1")).toBeInTheDocument(),
    );
    await act(async () => {
      fireEvent.click(screen.getByText("Location 1"));
    });

    await waitFor(() =>
      expect(mockStore.placeSection.setLocationId).toHaveBeenCalledWith("loc1"),
    );
  });

  // test("closes the modal when done button is clicked", async () => {
  //   const setModalShowMock = jest.fn();
  //   renderComponent();
  //   const doneButton = screen.getByText("DONE");
  //   fireEvent.click(doneButton);
  //   expect(setModalShowMock).not.toHaveBeenCalled();
  // });

  // test("clears selected location when cancel button is clicked", async () => {
  //   renderComponent();
  //   fireEvent.click(screen.getByText("CANCEL"));
  //   await waitFor(() =>
  //     expect(mockStore.placeSection.setLocationId).toHaveBeenCalledWith(null),
  //   );
  // });
});
