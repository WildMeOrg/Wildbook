import React from "react";
import { fireEvent, screen, waitFor, act } from "@testing-library/react";
import LoginPage from "../../../pages/Login";
import useLogin from "../../../models/auth/useLogin";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../models/auth/useLogin");

describe("LoginPage Tests", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    let errorState = null;
    const mockSetError = jest.fn((errorMessage) => {
      errorState = errorMessage;
      useLogin.mockReturnValue({
        authenticate: mockAuthenticate,
        error: errorState,
        setError: mockSetError,
        loading: false,
      });
    });

    const mockAuthenticate = jest.fn(async () => {
      mockSetError("Invalid email or password");
    });
    useLogin.mockReturnValue({
      authenticate: mockAuthenticate,
      error: errorState,
      setError: mockSetError,
      loading: false,
    });
  });

  test("should show error message when submitting empty username and password", async () => {
    renderWithProviders(<LoginPage />);

    await act(async () => {
      screen.getByRole("button", { name: /sign in/i }).click();
    });

    await waitFor(() => {
      expect(
        screen.getByText(/Invalid email or password/i),
      ).toBeInTheDocument();
    });
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

    await waitFor(() => {
      expect(
        screen.getByText(/Invalid email or password/i),
      ).toBeInTheDocument();
    });
  });
});
