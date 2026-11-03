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

  test("cannot submit an empty username and password", async () => {
    renderWithProviders(<LoginPage />);

    // the page used to call authenticate("", "") here; it now refuses the submit outright
    fireEvent.click(screen.getByRole("checkbox"));
    expect(screen.getByRole("button", { name: /sign in/i })).toBeDisabled();

    await act(async () => {
      screen.getByRole("button", { name: /sign in/i }).click();
    });

    expect(mockAuthenticate).not.toHaveBeenCalled();
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
      // Sign In stays disabled until the terms checkbox is ticked
      fireEvent.click(screen.getByRole("checkbox"));
      screen.getByRole("button", { name: /sign in/i }).click();
    });

    expect(mockSetError).toHaveBeenCalledWith(null);
    expect(mockAuthenticate).toHaveBeenCalledWith("wrongUser", "wrongPass");
  });
});
