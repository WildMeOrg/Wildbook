// DateSectionReview.test.js
import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/AttributesAndValueComponent", () => ({
  AttributesAndValueComponent: (props) => (
    <div data-testid={`attr-${props.attributeId}`}>
      <span data-testid={`id-${props.attributeId}`}>{props.attributeId}</span>
      <span data-testid={`val-${props.attributeId}`}>
        {String(props.value ?? "")}
      </span>
    </div>
  ),
}));

import * as Mod from "../../../pages/Encounter/DateSectionReview";
const DateSectionReview = Mod.DateSectionReview || Mod.default;

const makeStore = (values = {}) => ({
  getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
});

describe("DateSectionReview", () => {
  test("formats ISO with T, milliseconds and Z", () => {
    const store = makeStore({
      date: { date: "2025-01-02T03:04:05.123Z", verbatimEventDate: "raw" },
    });
    render(<DateSectionReview store={store} />);
    expect(screen.getByTestId("val-DATE")).toHaveTextContent(
      "2025-01-02 03:04:05",
    );
    expect(screen.getByTestId("val-VERBATIM_EVENT_DATE")).toHaveTextContent(
      "raw",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith("date", "date");
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "date",
      "verbatimEventDate",
    );
  });

  test("formats ISO with timezone offset", () => {
    const store = makeStore({
      date: { date: "2024-06-07T08:09:10+02:00", verbatimEventDate: "" },
    });
    render(<DateSectionReview store={store} />);
    expect(screen.getByTestId("val-DATE")).toHaveTextContent(
      "2024-06-07 08:09:10",
    );
  });

  test("renders empty string when date is undefined/null", () => {
    const store = makeStore({
      date: { date: undefined, verbatimEventDate: "verbatim" },
    });
    render(<DateSectionReview store={store} />);
    expect(screen.getByTestId("val-DATE")).toHaveTextContent("");
    expect(screen.getByTestId("val-VERBATIM_EVENT_DATE")).toHaveTextContent(
      "verbatim",
    );
  });

  test("keeps seconds when no milliseconds present", () => {
    const store = makeStore({
      date: { date: "2023-12-31T23:59:59Z", verbatimEventDate: "" },
    });
    render(<DateSectionReview store={store} />);
    expect(screen.getByTestId("val-DATE")).toHaveTextContent(
      "2023-12-31 23:59:59",
    );
  });
});
