import React from "react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import AuthenticatedAppHeader from "../../../components/AuthenticatedAppHeader";
import AuthContext from "../../../AuthProvider";
import FooterVisibilityContext from "../../../FooterVisibilityContext";
import LocaleContext from "../../../IntlProvider";

// Mock the logo component to avoid unnecessary rendering issues
jest.mock("../../../components/svg/Logo", () => {
  const React = require("react");
  const mockLogo = () => React.createElement("div", { "data-testid": "logo" });
  mockLogo.displayName = "Logo";
  return mockLogo;
});

// Helper function to render with required context providers
const renderComponent = (footerVisible = true, authContextValue = {}) => {
  const mockOnLocaleChange = jest.fn();
  return renderWithProviders(
    <FooterVisibilityContext.Provider value={{ visible: footerVisible }}>
      <AuthContext.Provider
        value={{
          count: 5,
          collaborationTitle: "Collaboration Test",
          collaborationData: [],
          mergeData: [],
          getAllNotifications: jest.fn(),
          ...authContextValue,
        }}
      >
        <LocaleContext.Provider value={{ onLocaleChange: mockOnLocaleChange }}>
          <AuthenticatedAppHeader
            username="testuser"
            avatar="test-avatar-url"
            showclassicsubmit={true}
            showClassicEncounterSearch={false}
          />
        </LocaleContext.Provider>
      </AuthContext.Provider>
    </FooterVisibilityContext.Provider>,
  );
};

describe("AuthenticatedAppHeader Component", () => {
  test("renders the header when footer is visible", () => {
    renderComponent(true);
    expect(screen.getByTestId("logo")).toBeInTheDocument();
    expect(screen.getByText(`${process.env.SITE_NAME}`)).toBeInTheDocument();
  });

  test("does not render header when footer is not visible", () => {
    renderComponent(false);
    expect(screen.queryByTestId("logo")).not.toBeInTheDocument();
  });

  test("displays the correct avatar", () => {
    renderComponent(true);
    const img = screen.getAllByRole("img");
    const avatarImg = img.find((image) =>
      image.src.includes("test-avatar-url"),
    );
    expect(avatarImg).toBeInTheDocument();
  });

  test("renders notification button with correct count", () => {
    renderComponent(true, { count: 3 });
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  test("renders multiLanguageDropdown", () => {
    renderComponent(true);
    expect(screen.getByTestId("language-dropdown")).toBeInTheDocument();
  });

  test("renders notification button", () => {
    renderComponent(true);
    expect(screen.getByTestId("notification-button")).toBeInTheDocument();
  });

  test("renders header-quick-search", () => {
    renderComponent(true);
    expect(screen.getByTestId("header-quick-search")).toBeInTheDocument();
  });

  test("renders submit menu", () => {
    renderComponent(true);
    expect(screen.getByText("Submit")).toBeInTheDocument();
  });
});
