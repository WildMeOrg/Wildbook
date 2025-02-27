import useLogin from "../../../models/auth/useLogin";
jest.mock("../../../models/auth/useLogin");
import React from "react";
import LoginPage from "../../../pages/Login";
import { renderWithProviders } from "../../../utils/utils";
import { screen } from "@testing-library/react";

jest.mock("../../../models/auth/useLogin");

describe("LoginPage - Button State", () => {
  test("disables submit button when loading", () => {
    useLogin.mockReturnValue({
      authenticate: jest.fn(),
      error: null,
      setError: jest.fn(),
      loading: true,
    });

    renderWithProviders(<LoginPage />);
    const button = screen.getByRole("button", { name: /sign in/i });
    expect(button).toBeDisabled();
  });
});
