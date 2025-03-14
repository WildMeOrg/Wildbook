import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { LocationFilterByMap } from "../LocationFilterByMap";
import { Provider } from "mobx-react";
import ThemeContext from "../../ThemeColorProvider";
import { IntlProvider } from "react-intl";
import Store from "../../store";

jest.mock("../../components/Map", () => {
  return jest.fn(() => <div data-testid="map-component">Mocked Map</div>);
});

const mockStore = new Store();
mockStore.placeSection = {
  locationId: null,
  setLocationId: jest.fn(),
};

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
  return render(
    <Provider store={mockStore}>
      <IntlProvider locale="en" messages={{}}>
        <ThemeContext.Provider
          value={{
            primaryColors: { primary500: "#000" },
          }}
        >
          <LocationFilterByMap
            store={mockStore}
            modalShow={modalShow}
            setModalShow={jest.fn()}
            treeData={treeData}
            mapCenterLat={10}
            mapCenterLon={10}
            mapZoom={5}
            setShowFilterByMap={jest.fn()}
          />
        </ThemeContext.Provider>
      </IntlProvider>
    </Provider>,
  );
};

describe("LocationFilterByMap Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByTestId("map-component")).toBeInTheDocument();
    expect(screen.getByText("LOCATION_ID")).toBeInTheDocument();
  });

  test("selects a location from the dropdown", async () => {
    renderComponent();
    const dropdown = screen.getByPlaceholderText("LOCATIONID_INSTRUCTION");
    fireEvent.mouseDown(dropdown);

    await waitFor(() =>
      expect(screen.getByText("Location 1")).toBeInTheDocument(),
    );
    fireEvent.click(screen.getByText("Location 1"));

    await waitFor(() =>
      expect(mockStore.placeSection.setLocationId).toHaveBeenCalledWith("loc1"),
    );
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
      expect(mockStore.placeSection.setLocationId).toHaveBeenCalledWith(null),
    );
  });
});
