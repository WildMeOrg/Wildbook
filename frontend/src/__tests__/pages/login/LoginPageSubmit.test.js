import useLogin from "../../../models/auth/useLogin";
jest.mock("../../../models/auth/useLogin");
import React from "react";
import LoginPage from "../../../pages/Login";
import {
  renderWithProviders,
  fireInput,
  clickButton,
} from "../../../utils/utils";
import { waitFor } from "@testing-library/react";

jest.mock("../../../models/auth/useLogin");

describe("LoginPage - Form Submission", () => {
  let mockAuthenticate;

  beforeEach(() => {
    mockAuthenticate = jest.fn();
    useLogin.mockReturnValue({
      authenticate: mockAuthenticate,
      error: null,
      setError: jest.fn(),
      loading: false,
    });
  });

  test("calls authenticate function on submit", async () => {
    renderWithProviders(<LoginPage />);
    fireInput("Username", "testuser");
    fireInput("Password", "password123");
    clickButton("Sign In");

    await waitFor(() =>
      expect(mockAuthenticate).toHaveBeenCalledWith("testuser", "password123"),
    );
  });
});
