import React from "react";
import { renderWithProviders } from "../../../utils/utils";
import LoginPage from "../../../pages/Login";
import { fireEvent, screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";
import { useSiteSettings } from "../../../SiteSettingsContext";

jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));

test("allows user to type username and password", () => {
  useSiteSettings.mockReturnValue({
    data: {},
    isLoading: false,
    error: null,
  });
  renderWithProviders(<LoginPage />);

  const usernameInput = screen.getByPlaceholderText("Username");
  const passwordInput = screen.getByPlaceholderText("Password");

  act(() => {
    fireEvent.change(usernameInput, { target: { value: "testuser" } });
  });

  act(() => {
    fireEvent.change(passwordInput, { target: { value: "password123" } });
  });

  expect(usernameInput.value).toBe("testuser");
  expect(passwordInput.value).toBe("password123");
});
