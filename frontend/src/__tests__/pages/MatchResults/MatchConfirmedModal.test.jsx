import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchConfirmedModal from "../../../pages/MatchResultsPage/components/MatchConfirmedModal";

const themeColor = {
  primaryColors: { primary500: "#00ACCE" },
};

const renderModal = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MatchConfirmedModal
        show={true}
        onHide={jest.fn()}
        encounterId="enc-123"
        encounterCount={0}
        individualId="ind-456"
        individualName="Luna"
        themeColor={themeColor}
        {...props}
      />
    </IntlProvider>,
  );

describe("MatchConfirmedModal", () => {
  test("does not render when show is false", () => {
    renderModal({ show: false });
    expect(screen.queryByText("MATCH_CONFIRMED")).not.toBeInTheDocument();
  });

  test("renders modal title when show is true", () => {
    renderModal();
    expect(screen.getByText("MATCH_CONFIRMED")).toBeInTheDocument();
  });

  test("shows encounter link and individual link when encounterCount is 0", () => {
    renderModal({ encounterCount: 0, encounterId: "enc-abc" });
    expect(screen.getByText("enc-abc")).toBeInTheDocument();
    expect(screen.getByText("Luna")).toBeInTheDocument();
  });

  test("shows merge message when encounterCount > 0", () => {
    renderModal({ encounterCount: 3 });
    expect(screen.getByText(/YOU_MERGED_N_ENCOUNTERS/)).toBeInTheDocument();
    // No encounter link in merged case
    expect(screen.queryByText("enc-123")).not.toBeInTheDocument();
  });

  test("falls back to individualId when individualName is not provided", () => {
    renderModal({ individualName: null, individualId: "ind-999" });
    expect(screen.getByText("ind-999")).toBeInTheDocument();
  });

  test("Close button calls onHide and reloads page", () => {
    const onHide = jest.fn();
    const reload = jest.fn();
    Object.defineProperty(window, "location", {
      value: { reload },
      writable: true,
    });
    renderModal({ onHide });
    fireEvent.click(screen.getByText("CLOSE"));
    expect(onHide).toHaveBeenCalled();
    expect(reload).toHaveBeenCalled();
  });

  test("individual link points to individuals.jsp with correct id", () => {
    renderModal({ individualId: "ind-xyz" });
    const link = screen.getByText("Luna").closest("a");
    expect(link.href).toContain("individuals.jsp");
    expect(link.href).toContain("ind-xyz");
  });
});
