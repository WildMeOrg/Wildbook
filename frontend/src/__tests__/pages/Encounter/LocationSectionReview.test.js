import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/AttributesAndValueComponent", () => {
  const MockAttr = (props) => (
    <div data-testid={`attr-${props.attributeId}`}>
      <span data-testid={`id-${props.attributeId}`}>{props.attributeId}</span>
      <span data-testid={`val-${props.attributeId}`}>
        {String(props.value ?? "")}
      </span>
    </div>
  );
  MockAttr.displayName = "MockAttributesAndValueComponent";
  return { AttributesAndValueComponent: MockAttr };
});

jest.mock("../../../pages/Encounter/MapDisplay", () => {
  const MockMap = () => <div data-testid="map-display">MAP</div>;
  MockMap.displayName = "MockMapDisplay";
  return MockMap;
});

import { LocationSectionReview } from "../../../pages/Encounter/LocationSectionReview";

const makeStore = (vals = {}) => ({
  getFieldValue: jest.fn((section, key) => vals?.[section]?.[key]),
});

describe("LocationSectionReview", () => {
  test("renders LOCATION, LOCATION_ID, COUNTRY attributes", () => {
    const store = makeStore({
      location: {
        verbatimLocality: "Toronto Zoo",
        locationName: "CA-ON-TORONTO",
        country: "Canada",
      },
    });

    render(<LocationSectionReview store={store} />);

    expect(screen.getByTestId("attr-LOCATION")).toBeInTheDocument();
    expect(screen.getByTestId("val-LOCATION")).toHaveTextContent("Toronto Zoo");

    expect(screen.getByTestId("attr-LOCATION_ID")).toBeInTheDocument();
    expect(screen.getByTestId("val-LOCATION_ID")).toHaveTextContent(
      "CA-ON-TORONTO",
    );

    expect(screen.getByTestId("attr-COUNTRY")).toBeInTheDocument();
    expect(screen.getByTestId("val-COUNTRY")).toHaveTextContent("Canada");

    expect(store.getFieldValue).toHaveBeenCalledWith(
      "location",
      "verbatimLocality",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "location",
      "locationName",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith("location", "country");
  });

  test("renders coordinates when locationGeoPoint exists", () => {
    const store = makeStore({
      location: {
        verbatimLocality: "",
        locationName: "",
        country: "",
        locationGeoPoint: { lat: 43.653, lon: -79.383 },
      },
    });

    render(<LocationSectionReview store={store} />);

    expect(screen.getByText("COORDINATES")).toBeInTheDocument();
    expect(screen.getByText(/latitude: 43\.653/)).toBeInTheDocument();
    expect(screen.getByText(/longitude: -79\.383/)).toBeInTheDocument();
  });

  test("does not render lat/lon lines when locationGeoPoint is missing", () => {
    const store = makeStore({
      location: {
        verbatimLocality: "Somewhere",
      },
    });

    render(<LocationSectionReview store={store} />);

    expect(screen.getByText("COORDINATES")).toBeInTheDocument();
    expect(screen.queryByText(/latitude:/i)).toBeNull();
    expect(screen.queryByText(/longitude:/i)).toBeNull();
  });

  test("renders MAP heading and MapDisplay", () => {
    const store = makeStore();
    render(<LocationSectionReview store={store} />);

    expect(screen.getByTestId("map-display")).toBeInTheDocument();
  });
});
