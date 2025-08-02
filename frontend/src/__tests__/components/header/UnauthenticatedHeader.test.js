import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import UnAuthenticatedAppHeader from "../../../components/UnAuthenticatedAppHeader";
import FooterVisibilityContext from "../../../FooterVisibilityContext";
import { IntlProvider } from "react-intl";
import LocaleContext from "../../../IntlProvider";

jest.mock("../../../components/svg/Logo", () => {
  const React = require("react");
  const mockLogo = () => React.createElement("div", { "data-testid": "logo" });
  mockLogo.displayName = "Logo";
  return mockLogo;
});

jest.mock("../../../constants/navMenu", () => ({
  unAuthenticatedMenu: () => [
    {
      Submit: [
        {
          name: "Report an Encounter",
          href: "/report",
        },
      ],
    },
  ],
}));

const renderComponent = (visible = true, showclassicsubmit = true) => {
  const mockOnLocaleChange = jest.fn();
  render(
    <IntlProvider locale="en" onLocaleChange={jest.fn()}>
      <FooterVisibilityContext.Provider value={{ visible: visible }}>
        <LocaleContext.Provider value={{ onLocaleChange: mockOnLocaleChange }}>
          <UnAuthenticatedAppHeader showclassicsubmit={showclassicsubmit} />
        </LocaleContext.Provider>
      </FooterVisibilityContext.Provider>
    </IntlProvider>,
  );
};

describe("UnAuthenticatedAppHeader", () => {
  test("renders logo", () => {
    renderComponent(true), true;
    expect(screen.getByTestId("logo")).toBeInTheDocument();
  });

  test("renders site name", () => {
    renderComponent(true, true);
    expect(screen.getByText(`${process.env.SITE_NAME}`)).toBeInTheDocument();
  });

  test("renders navbar when visible is true", () => {
    renderComponent(true, true);
    expect(screen.getByText("LOGIN_LOGIN")).toBeInTheDocument();
  });

  test("does not render navbar when visible is false", () => {
    renderComponent(false, true);
    expect(screen.queryByRole("banner")).not.toBeInTheDocument();
  });

  test("renders menu items correctly", () => {
    renderComponent(true, true);
    expect(screen.getByText("SUBMIT")).toBeInTheDocument();
  });

  test("shows dropdown on hover", async () => {
    renderComponent(true, true);
    const menu = screen.getByText("SUBMIT");
    fireEvent.mouseEnter(menu);
    expect(menu.closest(".dropdown")).toHaveClass("show");
  });

  test("hides dropdown on mouse leave", async () => {
    renderComponent(true, true);
    const menu = screen.getByText("SUBMIT");
    fireEvent.mouseEnter(menu);
    fireEvent.mouseLeave(menu);
    expect(menu.closest(".dropdown")).not.toHaveClass("show");
  });

  test("renders sub-menu items when parent is hovered", async () => {
    renderComponent(true, true);
    const parentMenu = screen.getByText("SUBMIT");
    fireEvent.mouseEnter(parentMenu);
    expect(screen.getByText("Report an Encounter")).toBeInTheDocument();
  });
});
