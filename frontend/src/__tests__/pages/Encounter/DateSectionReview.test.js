// DateSectionReview.test.js
import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/AttributesAndValueComponent", () => ({
  AttributesAndValueComponent: ({ attributeId, value }) => (
    <div data-testid={`val-${attributeId}`}>{String(value ?? "")}</div>
  ),
}));

jest.mock("../../../pages/Encounter/stores/helperFunctions", () => ({
  formatDateValues: (v) => (v ? `formatted:${v}` : ""),
}));

import * as Mod from "../../../pages/Encounter/DateSectionReview";
const DateSectionReview = Mod.DateSectionReview || Mod.default;

const makeStore = (values = {}) => ({
  getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
});

describe("DateSectionReview", () => {
  test("renders formatted date and verbatim date", () => {
    const store = makeStore({
      date: {
        dateValues: "2025-01-02T03:04:05Z",
        verbatimEventDate: "raw text",
      },
    });

    render(<DateSectionReview store={store} />);

    expect(screen.getByTestId("val-DATE")).toHaveTextContent(
      "formatted:2025-01-02T03:04:05Z",
    );
    expect(screen.getByTestId("val-VERBATIM_EVENT_DATE")).toHaveTextContent(
      "raw text",
    );
  });

  test("renders empty formatted date when dateValues is missing", () => {
    const store = makeStore({
      date: {
        dateValues: undefined,
        verbatimEventDate: "verbatim",
      },
    });

    render(<DateSectionReview store={store} />);

    expect(screen.getByTestId("val-DATE")).toHaveTextContent("");
    expect(screen.getByTestId("val-VERBATIM_EVENT_DATE")).toHaveTextContent(
      "verbatim",
    );
  });
});
