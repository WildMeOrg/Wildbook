import React from "react";
import { render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchCriteriaDrawer from "../../../pages/MatchResultsPage/components/MatchCriteriaDrawer";

jest.mock("react-bootstrap", () => {
  const React = require("react");

  function Offcanvas({ show, children }) {
    if (!show) return null;
    return React.createElement("div", { "data-testid": "offcanvas" }, children);
  }
  Offcanvas.displayName = "MockOffcanvas";

  function OffcanvasHeader({ children }) {
    return React.createElement(
      "div",
      { "data-testid": "offcanvas-header" },
      children,
    );
  }
  OffcanvasHeader.displayName = "MockOffcanvasHeader";
  Offcanvas.Header = OffcanvasHeader;

  function OffcanvasTitle({ children }) {
    return React.createElement(
      "div",
      { "data-testid": "offcanvas-title" },
      children,
    );
  }
  OffcanvasTitle.displayName = "MockOffcanvasTitle";
  Offcanvas.Title = OffcanvasTitle;

  function OffcanvasBody({ children, style }) {
    return React.createElement(
      "div",
      { "data-testid": "offcanvas-body", style },
      children,
    );
  }
  OffcanvasBody.displayName = "MockOffcanvasBody";
  Offcanvas.Body = OffcanvasBody;

  return { Offcanvas };
});

const renderDrawer = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MatchCriteriaDrawer
        show={true}
        onHide={jest.fn()}
        filter={{}}
        {...props}
      />
    </IntlProvider>,
  );

describe("MatchCriteriaDrawer", () => {
  test("does not render when show is false", () => {
    renderDrawer({ show: false });
    expect(screen.queryByText("MATCH_CRITERIA")).not.toBeInTheDocument();
  });

  test("renders title when show is true", () => {
    renderDrawer();
    expect(screen.getByText("MATCH_CRITERIA")).toBeInTheDocument();
  });

  test("shows FILTER_SET_FOR_TASK message", () => {
    renderDrawer();
    expect(screen.getByText("FILTER_SET_FOR_TASK")).toBeInTheDocument();
  });

  test("shows location IDs when filter has locationIds", () => {
    renderDrawer({ filter: { locationIds: ["loc-1", "loc-2"] } });
    expect(screen.getByText(/loc-1, loc-2/)).toBeInTheDocument();
  });

  test("shows owner when filter has owner", () => {
    renderDrawer({ filter: { owner: "user@example.com" } });
    expect(screen.getByText(/user@example.com/)).toBeInTheDocument();
  });

  test("shows NO_FILTER_SET_FOR_TASK when filter is empty", () => {
    renderDrawer({ filter: {} });
    expect(screen.getByText("NO_FILTER_SET_FOR_TASK")).toBeInTheDocument();
  });

  test("shows NO_FILTER_SET_FOR_TASK when filter is null", () => {
    renderDrawer({ filter: null });
    expect(screen.getByText("NO_FILTER_SET_FOR_TASK")).toBeInTheDocument();
  });

  test("does not show location section when locationIds is empty", () => {
    renderDrawer({ filter: { locationIds: [] } });
    expect(screen.queryByText("LOCATION_IDS")).not.toBeInTheDocument();
  });
});
