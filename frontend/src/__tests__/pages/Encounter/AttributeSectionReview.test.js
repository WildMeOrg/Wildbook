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

import { AttributesSectionReview } from "../../../pages/Encounter/AttributesSectionReview";

const makeStore = (values = {}) => ({
  getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
});

const ATTRIBUTE_KEYS = [
  ["TAXONOMY", "taxonomy"],
  ["STATUS", "livingStatus"],
  ["SEX", "sex"],
  ["DISTINGUISHING_SCAR", "distinguishingScar"],
  ["BEHAVIOR", "behavior"],
  ["GROUP_ROLE", "groupRole"],
  ["PATTERNING_CODE", "patterningCode"],
  ["LIFE_STAGE", "lifeStage"],
  ["OBSERVATION_COMMENTS", "occurrenceRemarks"],
];

describe("AttributesSectionReview", () => {
  test("renders all AttributesAndValueComponent with correct ids and values", () => {
    const store = makeStore({
      attributes: {
        taxonomy: "mammal",
        livingStatus: "alive",
        sex: "female",
        distinguishingScar: "right-fin nick",
        behavior: "foraging",
        groupRole: "leader",
        patterningCode: "B",
        lifeStage: "juvenile",
        occurrenceRemarks: "near reef",
      },
    });

    render(<AttributesSectionReview store={store} />);

    ATTRIBUTE_KEYS.forEach(([id]) => {
      expect(screen.getByTestId(`attr-${id}`)).toBeInTheDocument();
      expect(screen.getByTestId(`id-${id}`)).toHaveTextContent(id);
    });

    expect(screen.getByTestId("val-TAXONOMY")).toHaveTextContent("mammal");
    expect(screen.getByTestId("val-STATUS")).toHaveTextContent("alive");
    expect(screen.getByTestId("val-SEX")).toHaveTextContent("female");
    expect(screen.getByTestId("val-DISTINGUISHING_SCAR")).toHaveTextContent(
      "right-fin nick",
    );
    expect(screen.getByTestId("val-BEHAVIOR")).toHaveTextContent("foraging");
    expect(screen.getByTestId("val-GROUP_ROLE")).toHaveTextContent("leader");
    expect(screen.getByTestId("val-PATTERNING_CODE")).toHaveTextContent("B");
    expect(screen.getByTestId("val-LIFE_STAGE")).toHaveTextContent("juvenile");
    expect(screen.getByTestId("val-OBSERVATION_COMMENTS")).toHaveTextContent(
      "near reef",
    );

    ATTRIBUTE_KEYS.forEach(([_, key]) => {
      expect(store.getFieldValue).toHaveBeenCalledWith("attributes", key);
    });
  });

  test("renders empty string when a value is undefined/null", () => {
    const store = makeStore({
      attributes: {
        taxonomy: undefined,
        livingStatus: null,
      },
    });

    render(<AttributesSectionReview store={store} />);

    expect(screen.getByTestId("val-TAXONOMY")).toHaveTextContent("");
    expect(screen.getByTestId("val-STATUS")).toHaveTextContent("");

    expect(screen.getByTestId("val-SEX")).toHaveTextContent("");
    expect(screen.getByTestId("val-OBSERVATION_COMMENTS")).toHaveTextContent(
      "",
    );
  });
});
