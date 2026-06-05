import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

import { MeasurementsReview } from "../../../pages/Encounter/MeasurementsReview";
import LocaleContext from "../../../IntlProvider";

const SAMPLING_PROTOCOLS = [
  {
    value: "directlymeasured",
    label: { en: "directly measured", es: "medido directamente" },
  },
  { value: "estimated", label: { en: "estimated", es: "estimada" } },
];

const renderWithLocale = (store, locale = "en") =>
  render(
    <LocaleContext.Provider value={{ locale }}>
      <MeasurementsReview store={store} />
    </LocaleContext.Provider>,
  );

const makeStore = (overrides = {}) => ({
  showMeasurements: true,
  measurementTypes: ["length", "weight"],
  encounterData: {
    measurements: [
      { type: "length", value: "230", units: "cm", samplingProtocol: "visual" },
      { type: "weight", value: "150", units: "kg", samplingProtocol: "scale" },
    ],
  },
  ...overrides,
});

describe("MeasurementsReview", () => {
  test("renders nothing when showMeasurements is false", () => {
    const store = makeStore({ showMeasurements: false });
    const { container } = render(<MeasurementsReview store={store} />);
    expect(container.firstChild).toBeEmptyDOMElement();
  });

  test("renders all measurementTypes even if encounterData has them", () => {
    const store = makeStore();
    render(<MeasurementsReview store={store} />);

    expect(screen.getByText("length")).toBeInTheDocument();
    expect(screen.getByText("230 cm (visual)")).toBeInTheDocument();

    expect(screen.getByText("weight")).toBeInTheDocument();
    expect(screen.getByText("150 kg (scale)")).toBeInTheDocument();
  });

  test("when a measurement type has no matching measurement, renders empty value", () => {
    const store = makeStore({
      measurementTypes: ["length", "girth"],
      encounterData: {
        measurements: [
          {
            type: "length",
            value: "230",
            units: "cm",
            samplingProtocol: "visual",
          },
        ],
      },
    });

    render(<MeasurementsReview store={store} />);

    expect(screen.getByText("length")).toBeInTheDocument();
    expect(screen.getByText("230 cm (visual)")).toBeInTheDocument();
    expect(screen.getByText("girth")).toBeInTheDocument();
  });

  test("works when encounterData.measurements is empty array", () => {
    const store = makeStore({
      encounterData: { measurements: [] },
    });

    render(<MeasurementsReview store={store} />);

    expect(screen.getByText("length")).toBeInTheDocument();
    expect(screen.getByText("weight")).toBeInTheDocument();
  });

  test("works when encounterData is missing", () => {
    const store = makeStore({
      encounterData: undefined,
    });

    render(<MeasurementsReview store={store} />);

    expect(screen.getByText("length")).toBeInTheDocument();
    expect(screen.getByText("weight")).toBeInTheDocument();
  });

  describe("sampling protocol localization", () => {
    test("localizes a samplingProtocol stored as the config key (classic submit.jsp)", () => {
      const store = makeStore({
        measurementTypes: ["length"],
        siteSettingsData: { samplingProtocol: SAMPLING_PROTOCOLS },
        encounterData: {
          measurements: [
            {
              type: "length",
              value: "50",
              units: "cm",
              samplingProtocol: "samplingProtocol1",
            },
          ],
        },
      });

      renderWithLocale(store, "en");

      expect(screen.getByText("50 cm (estimated)")).toBeInTheDocument();
      expect(screen.queryByText("50 cm (samplingProtocol1)")).toBeNull();
    });

    test("localizes a samplingProtocol stored as the config value (React edit)", () => {
      const store = makeStore({
        measurementTypes: ["length"],
        siteSettingsData: { samplingProtocol: SAMPLING_PROTOCOLS },
        encounterData: {
          measurements: [
            {
              type: "length",
              value: "50",
              units: "cm",
              samplingProtocol: "estimated",
            },
          ],
        },
      });

      renderWithLocale(store, "en");

      expect(screen.getByText("50 cm (estimated)")).toBeInTheDocument();
    });

    test("uses the active locale for the label", () => {
      const store = makeStore({
        measurementTypes: ["length"],
        siteSettingsData: { samplingProtocol: SAMPLING_PROTOCOLS },
        encounterData: {
          measurements: [
            {
              type: "length",
              value: "50",
              units: "cm",
              samplingProtocol: "samplingProtocol1",
            },
          ],
        },
      });

      renderWithLocale(store, "es");

      expect(screen.getByText("50 cm (estimada)")).toBeInTheDocument();
    });

    test("falls back to English when the active locale has no label", () => {
      const store = makeStore({
        measurementTypes: ["length"],
        siteSettingsData: { samplingProtocol: SAMPLING_PROTOCOLS },
        encounterData: {
          measurements: [
            {
              type: "length",
              value: "50",
              units: "cm",
              samplingProtocol: "samplingProtocol1",
            },
          ],
        },
      });

      renderWithLocale(store, "fr");

      expect(screen.getByText("50 cm (estimated)")).toBeInTheDocument();
    });

    test("falls back to the raw value when no matching protocol exists", () => {
      const store = makeStore({
        measurementTypes: ["length"],
        siteSettingsData: { samplingProtocol: SAMPLING_PROTOCOLS },
        encounterData: {
          measurements: [
            {
              type: "length",
              value: "50",
              units: "cm",
              samplingProtocol: "somethingUnknown",
            },
          ],
        },
      });

      renderWithLocale(store, "en");

      expect(screen.getByText("50 cm (somethingUnknown)")).toBeInTheDocument();
    });
  });
});
