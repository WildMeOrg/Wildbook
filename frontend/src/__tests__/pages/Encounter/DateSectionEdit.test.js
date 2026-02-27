/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/DateInput", () => (props) => (
  <input
    type="date"
    data-testid={`date-${props.label}`}
    value={props.value ?? ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

jest.mock("../../../components/generalInputs/TextInput", () => (props) => (
  <input
    data-testid={`text-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

jest.mock("react-bootstrap", () => ({
  Alert: (p) => <div role="alert">{p.children}</div>,
}));

import { DateSectionEdit } from "../../../pages/Encounter/DateSectionEdit";

const makeStore = (overrides = {}) => {
  const values = overrides._values || {};
  return {
    getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
    setFieldValue: jest.fn(),
    errors: {
      getFieldError: jest.fn(() => ""),
      hasSectionError: jest.fn(() => false),
      getSectionErrors: jest.fn(() => []),
    },
    ...overrides,
  };
};

describe("DateSectionEdit", () => {
  test("renders inputs with defaults when values are absent", () => {
    const store = makeStore();
    render(<DateSectionEdit store={store} />);

    expect(screen.getByTestId("date-ENCOUNTER_DATE")).toBeInTheDocument();
    expect(screen.getByTestId("text-VERBATIM_EVENT_DATE")).toBeInTheDocument();

    expect(screen.getByTestId("date-ENCOUNTER_DATE")).toHaveValue("");
    expect(screen.getByTestId("text-VERBATIM_EVENT_DATE")).toHaveValue("");
  });

  test("uses values from store.getFieldValue", () => {
    const store = makeStore({
      _values: {
        date: { dateValues: "2024-05-01", verbatimEventDate: "May 1st, 2024" },
      },
    });
    render(<DateSectionEdit store={store} />);

    expect(screen.getByTestId("date-ENCOUNTER_DATE")).toHaveValue("2024-05-01");
    expect(screen.getByTestId("text-VERBATIM_EVENT_DATE")).toHaveValue(
      "May 1st, 2024",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith("date", "dateValues");
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "date",
      "verbatimEventDate",
    );
  });

  test("changing date and verbatim triggers setFieldValue with correct keys", () => {
    const store = makeStore();
    render(<DateSectionEdit store={store} />);

    fireEvent.change(screen.getByTestId("date-ENCOUNTER_DATE"), {
      target: { value: "2025-01-02" },
    });
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "date",
      "dateValues",
      "2025-01-02",
    );

    fireEvent.change(screen.getByTestId("text-VERBATIM_EVENT_DATE"), {
      target: { value: "Jan 2, 2025" },
    });
    expect(store.setFieldValue).toHaveBeenCalledWith(
      "date",
      "verbatimEventDate",
      "Jan 2, 2025",
    );
  });

  test("shows field error for date when present", () => {
    const store = makeStore({
      errors: {
        getFieldError: jest.fn((section, key) =>
          section === "date" && key === "dateValues" ? "Invalid date" : "",
        ),
        hasSectionError: jest.fn(() => false),
        getSectionErrors: jest.fn(() => []),
      },
    });
    render(<DateSectionEdit store={store} />);

    expect(screen.getByText("Invalid date")).toBeInTheDocument();
  });

  test("shows section Alert when store has section errors", () => {
    const store = makeStore({
      errors: {
        getFieldError: jest.fn(() => ""),
        hasSectionError: jest.fn((section) => section === "date"),
        getSectionErrors: jest.fn(() => ["errA", "errB"]),
      },
    });
    render(<DateSectionEdit store={store} />);

    expect(screen.getByRole("alert")).toHaveTextContent("errA;errB");
  });
});
