/* eslint-disable react/display-name */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid={`select-${props.label}`}
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="" />
    {(props.options || []).map((opt) => (
      <option key={String(opt)} value={opt}>
        {String(opt)}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/generalInputs/TextInput", () => {
  const React = require("react");
  return (props) => {
    const [val, setVal] = React.useState(props.value || "");
    return (
      <input
        data-testid={`text-${props.label}`}
        value={val}
        onChange={(e) => {
          setVal(e.target.value);
          props.onChange && props.onChange(e.target.value);
        }}
      />
    );
  };
});

jest.mock(
  "../../../components/generalInputs/FreeTextAndSelectInput",
  () => (props) => (
    <input
      data-testid={`freetext-${props.label}`}
      value={props.value || ""}
      onChange={(e) => props.onChange && props.onChange(e.target.value)}
    />
  ),
);

jest.mock("react-bootstrap", () => ({
  Alert: (p) => <div role="alert">{p.children}</div>,
}));

import { AttributesSectionEdit } from "../../../pages/Encounter/AttributesSectionEdit";

const makeStore = (overrides = {}) => {
  const values = overrides._values || {};
  return {
    getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
    setFieldValue: jest.fn(),

    taxonomyOptions: ["mammal", "bird"],
    livingStatusOptions: ["alive", "dead"],
    sexOptions: ["male", "female"],
    behaviorOptions: ["sleeping", "foraging"],
    patterningCodeOptions: ["A", "B", "C"],
    lifeStageOptions: ["juvenile", "adult"],

    errors: {
      hasSectionError: jest.fn(() => false),
      getSectionErrors: jest.fn(() => []),
    },

    ...overrides,
  };
};

describe("AttributesSectionEdit", () => {
  test("renders all inputs with empty default values when store returns undefined", () => {
    const store = makeStore();
    render(<AttributesSectionEdit store={store} />);

    expect(screen.getByTestId("select-TAXONOMY")).toBeInTheDocument();
    expect(screen.getByTestId("select-LIVING_STATUS")).toBeInTheDocument();
    expect(screen.getByTestId("select-SEX")).toBeInTheDocument();
    expect(screen.getByTestId("select-PATTERNING_CODE")).toBeInTheDocument();
    expect(screen.getByTestId("select-LIFE_STAGE")).toBeInTheDocument();

    expect(screen.getByTestId("text-DISTINGUISHING_SCAR")).toBeInTheDocument();
    expect(screen.getByTestId("freetext-BEHAVIOR")).toBeInTheDocument();
    expect(screen.getByTestId("text-GROUP_ROLE")).toBeInTheDocument();
    expect(screen.getByTestId("text-OBSERVATION_COMMENTS")).toBeInTheDocument();

    expect(screen.getByTestId("select-TAXONOMY")).toHaveValue("");
    expect(screen.getByTestId("text-DISTINGUISHING_SCAR")).toHaveValue("");
  });

  test("select options are rendered", () => {
    const store = makeStore();
    render(<AttributesSectionEdit store={store} />);

    const taxonomy = screen.getByTestId("select-TAXONOMY");
    expect(taxonomy).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "mammal" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "bird" })).toBeInTheDocument();

    expect(
      screen.getByRole("option", { name: "juvenile" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "adult" })).toBeInTheDocument();
  });

  test("changing Select/Text/FreeText triggers setFieldValue with correct keys", async () => {
    const user = userEvent.setup();
    const store = makeStore();
    render(<AttributesSectionEdit store={store} />);
    await user.selectOptions(screen.getByTestId("select-SEX"), "female");
    await user.type(screen.getByTestId("text-DISTINGUISHING_SCAR"), "scar123");
    await user.type(screen.getByTestId("freetext-BEHAVIOR"), "sleeping");
    await user.selectOptions(screen.getByTestId("select-LIFE_STAGE"), "adult");
  });

  test("shows Alert with joined errors when store has section error", () => {
    const store = makeStore({
      errors: {
        hasSectionError: jest.fn((section) => section === "attributes"),
        getSectionErrors: jest.fn(() => ["errA", "errB"]),
      },
    });
    render(<AttributesSectionEdit store={store} />);

    const alert = screen.getByRole("alert");
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent("errA;errB");
  });

  test("no Alert when store has no section error", () => {
    const store = makeStore({
      errors: {
        hasSectionError: jest.fn(() => false),
        getSectionErrors: jest.fn(() => []),
      },
    });
    render(<AttributesSectionEdit store={store} />);

    expect(screen.queryByRole("alert")).toBeNull();
  });

  test("initial values come from store.getFieldValue", () => {
    const store = makeStore({
      _values: {
        attributes: {
          taxonomy: "mammal",
          livingStatus: "alive",
          sex: "male",
          distinguishingScar: "right-fin nick",
          behavior: "foraging",
          groupRole: "leader",
          patterningCode: "B",
          lifeStage: "juvenile",
          occurrenceRemarks: "near reef",
        },
      },
    });

    render(<AttributesSectionEdit store={store} />);

    expect(screen.getByTestId("select-TAXONOMY")).toHaveValue("mammal");
    expect(screen.getByTestId("select-LIVING_STATUS")).toHaveValue("alive");
    expect(screen.getByTestId("select-SEX")).toHaveValue("male");
    expect(screen.getByTestId("text-DISTINGUISHING_SCAR")).toHaveValue(
      "right-fin nick",
    );
    expect(screen.getByTestId("freetext-BEHAVIOR")).toHaveValue("foraging");
    expect(screen.getByTestId("text-GROUP_ROLE")).toHaveValue("leader");
    expect(screen.getByTestId("select-PATTERNING_CODE")).toHaveValue("B");
    expect(screen.getByTestId("select-LIFE_STAGE")).toHaveValue("juvenile");
    expect(screen.getByTestId("text-OBSERVATION_COMMENTS")).toHaveValue(
      "near reef",
    );
  });
});
