import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import NewIndividualCreatedModal from "../../../pages/MatchResultsPage/components/NewIndividualCreatedModal";

const themeColor = {
  primaryColors: { primary500: "#00ACCE" },
};

const renderModal = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <NewIndividualCreatedModal
        show={true}
        onHide={jest.fn()}
        encounterId="enc-001"
        individualName="Nemo"
        themeColor={themeColor}
        {...props}
      />
    </IntlProvider>,
  );

describe("NewIndividualCreatedModal", () => {
  test("does not render when show is false", () => {
    renderModal({ show: false });
    expect(
      screen.queryByText("NEW_INDIVIDUAL_CREATED"),
    ).not.toBeInTheDocument();
  });

  test("renders modal title when show is true", () => {
    renderModal();
    expect(screen.getByText("NEW_INDIVIDUAL_CREATED")).toBeInTheDocument();
  });

  test("displays the encounter ID as a link", () => {
    renderModal({ encounterId: "enc-abc" });
    const link = screen.getByText("enc-abc");
    expect(link.tagName).toBe("A");
    expect(link.href).toContain("enc-abc");
  });

  test("displays the individual name", () => {
    renderModal({ individualName: "Willy" });
    expect(screen.getByText(/Willy/)).toBeInTheDocument();
  });

  test("CLOSE button calls onHide", () => {
    const onHide = jest.fn();
    renderModal({ onHide });
    fireEvent.click(screen.getByText("CLOSE"));
    expect(onHide).toHaveBeenCalled();
  });
});
