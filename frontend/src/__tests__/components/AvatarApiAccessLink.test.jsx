import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { renderWithProviders } from "../../utils/utils";
import AvatarAndUserProfile from "../../components/header/AvatarAndUserProfile";
import AuthContext from "../../AuthProvider";
import LocaleContext from "../../IntlProvider";

// AvatarAndUserProfile uses useNavigate + AuthContext + i18n, so render via the repo's standard
// renderWithProviders (router + intl), wrapped in the same contexts the header tests use
// (mirror frontend/src/__tests__/components/header/AuthenticatedHeader.test.js).
describe("AvatarAndUserProfile", () => {
  it("includes an API Access link to /api-access", () => {
    renderWithProviders(
      <AuthContext.Provider value={{ count: 0, mergeData: [], getAllNotifications: jest.fn() }}>
        <LocaleContext.Provider value={{ onLocaleChange: jest.fn() }}>
          <AvatarAndUserProfile avatar={"test-avatar"} />
        </LocaleContext.Provider>
      </AuthContext.Provider>,
    );
    // The dropdown is hover-controlled (show={shows} toggled by onMouseEnter).
    // Fire mouseEnter on the dropdown container to open it before querying items.
    const dropdown = document.querySelector(".custom-nav-dropdown");
    fireEvent.mouseEnter(dropdown);
    const link = screen.getByText(/api access/i).closest("a");
    expect(link).toHaveAttribute("href", expect.stringContaining("/api-access"));
  });
});
