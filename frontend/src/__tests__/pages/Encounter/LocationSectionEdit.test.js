/* eslint-disable react/display-name */
import React, { Suspense } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../utils/converToTreeData", () => ({
  __esModule: true,
  default: jest.fn(() => [
    { value: "loc-1", label: "Location 1" },
    { value: "loc-2", label: "Location 2" },
  ]),
}));

jest.mock("../../../components/generalInputs/TextInput", () => (props) => (
  <input
    data-testid={`text-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid={`select-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="" />
    {(props.options || []).map((opt) => (
      <option key={opt.value || opt} value={opt.value || opt}>
        {opt.label || opt}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/generalInputs/CoordinatesInput", () => () => (
  <div data-testid="coordinates-input" />
));

jest.mock("antd/es/tree-select", () => {
  const TS = ({ value, onChange, treeData }) => (
    <select
      data-testid="tree-select"
      value={value || ""}
      onChange={(e) => onChange && onChange(e.target.value || undefined)}
    >
      <option value="">(none)</option>
      {(treeData || []).map((n) => (
        <option key={n.value} value={n.value}>
          {n.label}
        </option>
      ))}
    </select>
  );
  TS.displayName = "MockAntdTreeSelect";
  return TS;
});

jest.mock("react-bootstrap", () => ({
  Alert: (p) => <div role="alert">{p.children}</div>,
}));

import { LocationSectionEdit } from "../../../pages/Encounter/LocationSectionEdit";

const makeStore = (overrides = {}) => ({
  siteSettingsData: {
    locationData: { locationID: [] },
    country: ["Canada", "United States"],
  },
  getFieldValue: jest.fn(() => ""),
  setFieldValue: jest.fn(),
  errors: {
    hasSectionError: jest.fn(() => false),
    getSectionErrors: jest.fn(() => []),
  },
  ...overrides,
});

describe("LocationSectionEdit", () => {
  test("renders location text input, tree-select, country select, coordinates", () => {
    const store = makeStore();
    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    expect(screen.getByTestId("text-LOCATION")).toBeInTheDocument();
    expect(screen.getByTestId("select-COUNTRY")).toBeInTheDocument();
    expect(screen.getByTestId("coordinates-input")).toBeInTheDocument();

    expect(screen.getByText("LOCATION_ID")).toBeInTheDocument();
  });

  test("typing in LOCATION updates store", async () => {
    const store = makeStore();
    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    // await user.type(screen.getByTestId("text-LOCATION"), "Great Barrier Reef");
    fireEvent.change(screen.getByTestId("text-LOCATION"), {
      target: { value: "Great Barrier Reef" },
    });
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "location",
      "verbatimLocality",
      "Great Barrier Reef",
    );
  });

  test("selecting LOCATION_ID in tree-select updates store", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      getFieldValue: jest.fn((section, key) => {
        if (section === "location" && key === "locationId") return "";
        return "";
      }),
    });

    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    await user.selectOptions(screen.getByTestId("tree-select"), "loc-2");
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "location",
      "locationId",
      "loc-2",
    );
  });

  test("COUNTRY select uses siteSettingsData.country", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    const countrySelect = screen.getByTestId("select-COUNTRY");
    expect(countrySelect).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "Canada" })).toBeInTheDocument();
    expect(
      screen.getByRole("option", { name: "United States" }),
    ).toBeInTheDocument();

    await user.selectOptions(countrySelect, "Canada");
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "location",
      "country",
      "Canada",
    );
  });

  test("shows Alert when location section has errors", () => {
    const store = makeStore({
      errors: {
        hasSectionError: jest.fn(() => true),
        getSectionErrors: jest.fn(() => ["lat is invalid", "lng is invalid"]),
      },
    });

    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("lat is invalid;lng is invalid");
  });

  test("Tree data is built from siteSettingsData.locationData", () => {
    const convertToTreeDataWithName =
      require("../../../utils/cconvertToTreeDataWithName").default;
    const store = makeStore({
      siteSettingsData: {
        locationData: { locationID: [{ id: 1, name: "X" }] },
        country: [],
      },
    });

    render(
      <Suspense fallback={<div>fallback...</div>}>
        <LocationSectionEdit store={store} />
      </Suspense>,
    );

    expect(convertToTreeDataWithName).toHaveBeenCalledWith(
      store.siteSettingsData.locationData.locationID,
      "value",
      "label",
    );
  });
});
