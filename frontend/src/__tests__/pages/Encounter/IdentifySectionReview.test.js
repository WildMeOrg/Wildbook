import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/AttributesAndValueComponent", () => {
  const MockAttr = (props) => (
    <div data-testid={`attr-${props.attributeId}`}>
      <span data-testid={`id-${props.attributeId}`}>{props.attributeId}</span>
      <span data-testid={`val-${props.attributeId}`}>
        {String(props.value ?? "")}
      </span>
    </div>
  );
  MockAttr.displayName = "MockAttributesAndValueComponent";
  return { AttributesAndValueComponent: MockAttr };
});

import { IdentifySectionReview } from "../../../pages/Encounter/IdentifySectionReview";

const makeStore = (values = {}) => ({
  getFieldValue: jest.fn((section, key) => values?.[section]?.[key]),
});

const FIELDS = [
  ["IDENTIFIED_AS", "individualDisplayName"],
  ["MATCHED_BY", "identificationRemarks"],
  ["ALTERNATE_ID", "otherCatalogNumbers"],
  ["SIGHTING_ID", "occurrenceId"],
];

describe("IdentifySectionReview", () => {
  test("renders all attributes with correct ids and values", () => {
    const store = makeStore({
      identify: {
        individualDisplayName: "Flipper",
        identificationRemarks: "auto",
        otherCatalogNumbers: "ALT-123",
        occurrenceId: "occ-999",
      },
    });

    render(<IdentifySectionReview store={store} />);

    FIELDS.forEach(([id]) => {
      expect(screen.getByTestId(`attr-${id}`)).toBeInTheDocument();
      expect(screen.getByTestId(`id-${id}`)).toHaveTextContent(id);
    });

    expect(screen.getByTestId("val-IDENTIFIED_AS")).toHaveTextContent(
      "Flipper",
    );
    expect(screen.getByTestId("val-MATCHED_BY")).toHaveTextContent("auto");
    expect(screen.getByTestId("val-ALTERNATE_ID")).toHaveTextContent("ALT-123");
    expect(screen.getByTestId("val-SIGHTING_ID")).toHaveTextContent("occ-999");

    FIELDS.forEach(([_, key]) => {
      expect(store.getFieldValue).toHaveBeenCalledWith("identify", key);
    });
  });

  test("renders empty string when store returns undefined", () => {
    const store = makeStore({ identify: {} });
    render(<IdentifySectionReview store={store} />);

    expect(screen.getByTestId("val-IDENTIFIED_AS")).toHaveTextContent("");
    expect(screen.getByTestId("val-MATCHED_BY")).toHaveTextContent("");
    expect(screen.getByTestId("val-ALTERNATE_ID")).toHaveTextContent("");
    expect(screen.getByTestId("val-SIGHTING_ID")).toHaveTextContent("");
  });
});
