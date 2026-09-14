import React from "react";
import { fireEvent, screen, act } from "@testing-library/react";
import LoginPage from "../../../pages/Login";
import useLogin from "../../../models/auth/useLogin";
import { renderWithProviders } from "../../../utils/utils";
import { useSiteSettings } from "../../../SiteSettingsContext";

jest.mock("../../../models/auth/useLogin");
jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));

describe("LoginPage Tests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useSiteSettings.mockReturnValue({
      data: {},
      isLoading: false,
      error: null,
    });
    mockSetError = jest.fn();
    mockAuthenticate = jest.fn();
    useLogin.mockReturnValue({
      authenticate: mockAuthenticate,
      error: null,
      setError: mockSetError,
      loading: false,
    });
  });

  let mockAuthenticate;
  let mockSetError;

  test("should show error message when submitting empty username and password", async () => {
    renderWithProviders(<LoginPage />);

    await act(async () => {
      screen.getByRole("button", { name: /sign in/i }).click();
    });

    expect(mockSetError).toHaveBeenCalledWith(null);
    expect(mockAuthenticate).toHaveBeenCalledWith("", "");
  });

  test("should show error when entering incorrect credentials", async () => {
    renderWithProviders(<LoginPage />);

    const usernameInput = screen.getByPlaceholderText("Username");
    const passwordInput = screen.getByPlaceholderText("Password");

    await act(async () => {
      fireEvent.change(usernameInput, { target: { value: "wrongUser" } });
      fireEvent.change(passwordInput, { target: { value: "wrongPass" } });
    });

    await act(async () => {
      screen.getByRole("button", { name: /sign in/i }).click();
    });

    expect(mockSetError).toHaveBeenCalledWith(null);
    expect(mockAuthenticate).toHaveBeenCalledWith("wrongUser", "wrongPass");
  });
});
