import useLogin from "../../../models/auth/useLogin";
jest.mock("../../../models/auth/useLogin");
import React from "react";
import { fireEvent, screen } from "@testing-library/react";
import LoginPage from "../../../pages/Login";
import { renderWithProviders } from "../../../utils/utils";

test("calls authenticate function on submit", () => {
  const mockAuthenticate = jest.fn();
  useLogin.mockReturnValue({
    authenticate: mockAuthenticate,
    error: null,
    setError: jest.fn(),
    loading: false,
  });

  renderWithProviders(<LoginPage />);

  fireEvent.change(screen.getByPlaceholderText("Username"), {
    target: { value: "testuser" },
  });

  fireEvent.change(screen.getByPlaceholderText("Password"), {
    target: { value: "password123" },
  });

  fireEvent.click(screen.getByRole("button", { name: /Sign In/i }));

  expect(mockAuthenticate).toHaveBeenCalledWith("testuser", "password123");
});
