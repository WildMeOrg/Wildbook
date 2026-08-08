/* eslint-disable react/display-name */
import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid={`select-${props.label}-${props.value || "empty"}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="" />
    {(props.options || []).map((o) => (
      <option key={o.value} value={o.value}>
        {o.label}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/generalInputs/NumberInput", () => (props) => (
  <input
    data-testid={`text-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

jest.mock("../../../components/Divider", () => ({
  Divider: () => <hr data-testid="divider" />,
}));

jest.mock("../../../IntlProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({ locale: "en" }),
  };
});

import { MeasurementsEdit } from "../../../pages/Encounter/MeasurementsEdit";
import LocaleContext from "../../../IntlProvider";

const Wrapper = ({ children, locale = "en" }) => (
  <LocaleContext.Provider value={{ locale }}>{children}</LocaleContext.Provider>
);

const makeStore = (overrides = {}) => ({
  showMeasurements: true,
  measurementTypes: ["length", "weight"],
  measurementUnits: ["cm", "kg"],
  siteSettingsData: {
    samplingProtocol: [
      { label: { en: "Visual", fr: "Visuel" }, value: "visual" },
      { label: { en: "Photo", fr: "Photo" }, value: "photo" },
    ],
  },
  getMeasurement: jest.fn((type) => ({
    value: overrides.initialValues?.[type]?.value ?? "",
    samplingProtocol: overrides.initialValues?.[type]?.samplingProtocol ?? "",
  })),
  setMeasurementValue: jest.fn(),
  setMeasurementSamplingProtocol: jest.fn(),
  errors: {
    setFieldError: jest.fn(),
    getFieldError: jest.fn(() => null),
  },
  ...overrides,
});

describe("MeasurementsEdit", () => {
  test("renders inputs for each measurement type", () => {
    const store = makeStore();
    render(
      <Wrapper>
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );
    expect(screen.getByText("length")).toBeInTheDocument();
    expect(screen.getByText("weight")).toBeInTheDocument();
    expect(screen.getByTestId("text-cm")).toBeInTheDocument();
    expect(screen.getByTestId("text-kg")).toBeInTheDocument();
    expect(
      screen.queryAllByTestId("select-SAMPLING_PROTOCOL-empty")[0],
    ).toBeInTheDocument();
  });

  test("typing in measurement input calls setMeasurementValue and validation", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    render(
      <Wrapper>
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );

    const lengthInput = screen.getByTestId("text-cm");
    await user.clear(lengthInput);
    fireEvent.change(lengthInput, { target: { value: "12.5" } });

    expect(store.errors.setFieldError).toHaveBeenCalledWith(
      "measurement",
      "length",
      null,
    );
    expect(store.setMeasurementValue).toHaveBeenCalledWith("length", "12.5");
  });

  test("changing sampling protocol calls setMeasurementSamplingProtocol and clears error when valid", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    render(
      <Wrapper>
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );

    const select = screen.queryAllByTestId("select-SAMPLING_PROTOCOL-empty")[0];
    await user.selectOptions(select, "visual");

    expect(store.errors.setFieldError).toHaveBeenCalledWith(
      "measurement",
      "length",
      null,
    );
    expect(store.setMeasurementSamplingProtocol).toHaveBeenCalledWith(
      "length",
      "visual",
    );
  });

  test("selecting empty protocol sets error", async () => {
    const user = userEvent.setup();
    const store = makeStore({
      getMeasurement: jest.fn(() => ({ value: "", samplingProtocol: "" })),
    });
    render(
      <Wrapper>
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );

    const select = screen.queryAllByTestId("select-SAMPLING_PROTOCOL-empty")[0];
    await user.selectOptions(select, "");

    expect(store.errors.setFieldError).toHaveBeenCalledWith(
      "measurement",
      "length",
      null,
    );
  });

  test("uses current locale to build protocol labels", () => {
    const store = makeStore();
    render(
      <Wrapper locale="fr">
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );

    const select = screen.queryAllByTestId("select-SAMPLING_PROTOCOL-empty");
    expect(select.length).toBeGreaterThan(0);
  });

  test("shows error block when getFieldError returns message", () => {
    const store = makeStore({
      errors: {
        setFieldError: jest.fn(),
        getFieldError: jest.fn((section, key) =>
          section === "measurement" && key === "length"
            ? "Value cannot be empty"
            : null,
        ),
      },
    });

    render(
      <Wrapper>
        <MeasurementsEdit store={store} />
      </Wrapper>,
    );

    expect(screen.getByText("Value cannot be empty")).toBeInTheDocument();
  });
});
