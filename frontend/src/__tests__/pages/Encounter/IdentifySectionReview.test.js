import React from "react";
import { render, screen } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
  useIntl: () => ({
    formatMessage: ({ defaultMessage, id }) => defaultMessage ?? id,
  }),
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

describe("IdentifySectionReview", () => {
  test("renders identify fields and SIGHTING_ID link", () => {
    const store = makeStore({
      identify: {
        individualDisplayName: "Flipper",
        individualId: "indiv-7",
        identificationRemarks: "auto",
        otherCatalogNumbers: "ALT-123",
        occurrenceId: "occ-999",
      },
    });

    render(<IdentifySectionReview store={store} />);

    expect(screen.getByText("IDENTIFIED_AS")).toBeInTheDocument();
    expect(screen.getByTestId("attr-MATCHED_BY")).toBeInTheDocument();
    expect(screen.getByTestId("attr-ALTERNATE_ID")).toBeInTheDocument();

    // the identified-as value is rendered as a link to the individual
    const individualLink = screen.getByRole("link", { name: "Flipper" });
    expect(individualLink).toHaveAttribute(
      "href",
      "/individuals.jsp?id=indiv-7",
    );
    expect(screen.getByTestId("val-MATCHED_BY")).toHaveTextContent("auto");
    expect(screen.getByTestId("val-ALTERNATE_ID")).toHaveTextContent("ALT-123");

    expect(screen.getByText("SIGHTING_ID")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "occ-999" });
    expect(link).toHaveAttribute("href", "/occurrence.jsp?number=occ-999");
    expect(link).toHaveAttribute("target", "_blank");

    expect(store.getFieldValue).toHaveBeenCalledWith(
      "identify",
      "individualDisplayName",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "identify",
      "identificationRemarks",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "identify",
      "otherCatalogNumbers",
    );
    expect(store.getFieldValue).toHaveBeenCalledWith(
      "identify",
      "occurrenceId",
    );
  });

  test("renders empty strings when store values are missing", () => {
    const store = makeStore({ identify: {} });

    render(<IdentifySectionReview store={store} />);

    // with no individual on the store the heading still renders but the link does not
    expect(screen.getByText("IDENTIFIED_AS")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Flipper" })).toBeNull();
    expect(screen.getByTestId("val-MATCHED_BY")).toHaveTextContent("");
    expect(screen.getByTestId("val-ALTERNATE_ID")).toHaveTextContent("");
  });
});
