import React from "react";
import { render, waitFor } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({
    data: {
      googleMapsKey: "FAKE_KEY",
      mapCenterLat: 10,
      mapCenterLon: 20,
    },
  }),
}));

jest.mock("@googlemaps/js-api-loader", () => {
  const loadMock = jest.fn(() => Promise.resolve());
  return {
    Loader: jest.fn(() => ({
      load: loadMock,
    })),
    __esModule: true,
  };
});
import { MapDisplay } from "../../../pages/Encounter/MapDisplay";

describe("MapDisplay", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    global.window.google = {
      maps: {
        Map: jest.fn(() => ({})),
        Marker: jest.fn(() => ({ setMap: jest.fn() })),
      },
    };
  });

  test("renders outer and inner div", () => {
    const store = { lat: undefined, lon: undefined };
    const { container } = render(<MapDisplay store={store} />);

    const outer = container.querySelector("div > div");
    expect(outer).toBeTruthy();
  });

  test("with coords uses them as center and creates marker", async () => {
    const store = { lat: 43.7, lon: -79.4 };
    render(<MapDisplay store={store} />);

    await waitFor(() => {
      expect(window.google.maps.Map).toHaveBeenCalled();
    });

    const mapCall = window.google.maps.Map.mock.calls[0];
    const mapOptions = mapCall[1];
    expect(mapOptions.center).toEqual({ lat: 43.7, lng: -79.4 });

    expect(window.google.maps.Marker).toHaveBeenCalledWith({
      position: { lat: 43.7, lng: -79.4 },
      map: expect.any(Object),
    });
  });

  test("without coords falls back to site settings defaultCenter", async () => {
    const store = { lat: undefined, lon: undefined };
    render(<MapDisplay store={store} zoom={5} />);

    await waitFor(() => {
      expect(window.google.maps.Map).toHaveBeenCalled();
    });

    const mapOptions = window.google.maps.Map.mock.calls[0][1];
    expect(mapOptions.center).toEqual({ lat: 10, lng: 20 });
    expect(window.google.maps.Marker).not.toHaveBeenCalled();
  });
});
