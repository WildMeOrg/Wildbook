import { fireEvent, screen, act, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import LoginPage from "../../../pages/Login";
import useLogin from "../../../models/auth/useLogin";
import React from "react";

jest.mock("../../../models/auth/useLogin");

test("displays error message when login fails", async () => {
  useLogin.mockImplementation(() => {
    const data = {
      authenticate: jest.fn(),
      error: "Invalid email or password",
      setError: jest.fn(),
      loading: false,
    };
    // console.log(data);
    return data;
  });

  renderWithProviders(<LoginPage />);

  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: /Sign In/i }));
  });

  await waitFor(() => {
    expect(screen.getByText(/Invalid email or password/i)).toBeInTheDocument();
  });

  const closeButton = screen.getByRole("button", { name: /close/i });
  fireEvent.click(closeButton);

  await waitFor(() => expect(alert).not.toBeInTheDocument());
});
