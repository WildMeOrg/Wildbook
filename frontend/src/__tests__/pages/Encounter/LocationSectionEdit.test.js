/* eslint-disable react/display-name */
import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

jest.mock("../../../utils/convertToTreeDataWithName", () => ({
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

jest.mock("../../../components/ContainerWithSpinner", () => (props) => (
  <div data-testid="container-spinner">{props.children}</div>
));

jest.mock("antd/es/tree-select", () => ({
  __esModule: true,
  default: ({ value, onChange, treeData }) => (
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
  ),
}));

jest.mock("react-bootstrap", () => ({
  Alert: (p) => <div role="alert">{p.children}</div>,
}));

import { LocationSectionEdit } from "../../../pages/Encounter/LocationSectionEdit";

const makeStore = (overrides = {}) => ({
  siteSettingsLoading: false,
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
  test("renders location text input, tree-select, country select, coordinates", async () => {
    const store = makeStore();
    render(<LocationSectionEdit store={store} />);

    expect(screen.getByTestId("text-LOCATION")).toBeInTheDocument();
    expect(screen.getByTestId("select-COUNTRY")).toBeInTheDocument();
    expect(screen.getByTestId("coordinates-input")).toBeInTheDocument();
    expect(screen.getByText("LOCATION_ID")).toBeInTheDocument();
    expect(await screen.findByTestId("tree-select")).toBeInTheDocument();
  });

  test("typing in LOCATION updates store", () => {
    const store = makeStore();
    render(<LocationSectionEdit store={store} />);

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

    render(<LocationSectionEdit store={store} />);

    await user.selectOptions(await screen.findByTestId("tree-select"), "loc-2");

    expect(store.setFieldValue).toHaveBeenCalledWith(
      "location",
      "locationId",
      "loc-2",
    );
  });

  test("COUNTRY select uses siteSettingsData.country", async () => {
    const user = userEvent.setup();
    const store = makeStore();

    render(<LocationSectionEdit store={store} />);

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

    render(<LocationSectionEdit store={store} />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("lat is invalid;lng is invalid");
  });

  test("Tree data is built from siteSettingsData.locationData", () => {
    const convertToTreeDataWithName =
      require("../../../utils/convertToTreeDataWithName").default;

    const store = makeStore({
      siteSettingsData: {
        locationData: { locationID: [{ id: 1, name: "X" }] },
        country: [],
      },
    });

    render(<LocationSectionEdit store={store} />);

    expect(convertToTreeDataWithName).toHaveBeenCalledWith(
      store.siteSettingsData.locationData.locationID,
    );
  });
});
