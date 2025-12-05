/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/TextInput", () => (props) => {
  return (
    <label>
      {props.label}
      <input
        data-testid={props.label}
        value={props.value}
        onChange={(e) => props.onChange && props.onChange(e.target.value)}
      />
    </label>
  );
});

jest.mock("../../../components/Divider", () => ({
  Divider: () => <hr data-testid="divider" />,
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => {
  return (
    <label>
      {props.label}
      <select
        data-testid={props.label}
        value={props.value}
        onChange={(e) => props.onChange && props.onChange(e.target.value)}
      >
        <option value="">--</option>
        {(props.options || []).map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
});

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

import { TrackingEdit } from "../../../pages/Encounter/TrackingEdit";

const makeStore = (overrides = {}) => ({
  metalTagsEnabled: true,
  metalTagLocation: ["left fin", "right fin"],
  metalTagValues: [{ location: "left fin", number: "LF-001" }],
  setMetalTagValues: jest.fn(),

  acousticTagEnabled: true,
  acousticTagValues: {
    serialNumber: "AC-123",
    idNumber: "ID-999",
  },
  setAcousticTagValues: jest.fn(),

  satelliteTagEnabled: true,
  satelliteTagValues: {
    name: "SPOT",
    serialNumber: "SAT-777",
    argosPttNumber: "PTT-888",
  },
  satelliteTagNameOptions: [
    { label: "SPOT", value: "SPOT" },
    { label: "SPLASH", value: "SPLASH" },
  ],
  setSatelliteTagValues: jest.fn(),

  ...overrides,
});

describe("TrackingEdit", () => {
  test("renders all enabled sections", () => {
    const store = makeStore();
    render(<TrackingEdit store={store} />);

    expect(screen.getByText("METAL_TAGS")).toBeInTheDocument();
    expect(screen.getByText("ACOUSTIC_TAGS")).toBeInTheDocument();
    expect(screen.getByText("SATELLITE_TAGS")).toBeInTheDocument();
  });

  test("editing existing metal tag updates correct item", () => {
    const store = makeStore();
    render(<TrackingEdit store={store} />);

    const leftInput = screen.getByTestId("left fin");
    fireEvent.change(leftInput, { target: { value: "LF-002" } });

    expect(store.setMetalTagValues).toHaveBeenCalledTimes(1);
    const arg = store.setMetalTagValues.mock.calls[0][0];
    expect(arg).toEqual([{ location: "left fin", number: "LF-002" }]);
  });

  test("typing into a metal tag location that had no value will append a new entry", () => {
    const store = makeStore();
    render(<TrackingEdit store={store} />);

    const rightInput = screen.getByTestId("right fin");
    fireEvent.change(rightInput, { target: { value: "RF-100" } });

    expect(store.setMetalTagValues).toHaveBeenCalledTimes(1);
    const arg = store.setMetalTagValues.mock.calls[0][0];
    expect(arg).toEqual(
      expect.arrayContaining([
        { location: "left fin", number: "LF-001" },
        { location: "right fin", number: "RF-100" },
      ]),
    );
  });

  test("acoustic tag fields call setAcousticTagValues", () => {
    const store = makeStore();
    render(<TrackingEdit store={store} />);

    const serialInput = screen.queryAllByTestId("SERIAL_NUMBER")[0];
    fireEvent.change(serialInput, { target: { value: "AC-999" } });

    expect(store.setAcousticTagValues).toHaveBeenCalledWith({
      serialNumber: "AC-999",
    });

    const idInput = screen.getByTestId("ID");
    fireEvent.change(idInput, { target: { value: "ID-000" } });

    expect(store.setAcousticTagValues).toHaveBeenCalledWith({
      idNumber: "ID-000",
    });
  });

  test("satellite tag fields call setSatelliteTagValues", () => {
    const store = makeStore();
    render(<TrackingEdit store={store} />);

    const nameSelect = screen.getByTestId("NAME");
    fireEvent.change(nameSelect, { target: { value: "SPLASH" } });
    expect(store.setSatelliteTagValues).toHaveBeenCalledWith({
      name: "SPLASH",
    });

    const serialInput = screen.queryAllByTestId("SERIAL_NUMBER")[1];
    fireEvent.change(serialInput, { target: { value: "SAT-999" } });
    expect(store.setSatelliteTagValues).toHaveBeenCalledWith({
      serialNumber: "SAT-999",
    });

    const pttInput = screen.getByTestId("ARGOS_PTT_NUMBER");
    fireEvent.change(pttInput, { target: { value: "PTT-000" } });
    expect(store.setSatelliteTagValues).toHaveBeenCalledWith({
      argosPttNumber: "PTT-000",
    });
  });

  test("when a section is disabled it does not render inputs", () => {
    const store = makeStore({
      metalTagsEnabled: false,
      acousticTagEnabled: false,
      satelliteTagEnabled: false,
    });
    render(<TrackingEdit store={store} />);

    expect(screen.queryByText("ACOUSTIC_TAGS")).not.toBeInTheDocument();
    expect(screen.queryByText("SATELLITE_TAGS")).not.toBeInTheDocument();
  });
});
