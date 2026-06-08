import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

import { MeasurementsReview } from "../../../pages/Encounter/MeasurementsReview";

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
    expect(screen.getByText("230 cm(visual)")).toBeInTheDocument();

    expect(screen.getByText("weight")).toBeInTheDocument();
    expect(screen.getByText("150 kg(scale)")).toBeInTheDocument();
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
    expect(screen.getByText("230 cm(visual)")).toBeInTheDocument();
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
});
