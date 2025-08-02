import { render, screen, act, waitFor } from "@testing-library/react";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { mockAxiosFailure } from "../../../utils/utils";
import { MemoryRouter } from "react-router-dom";
import React from "react";

jest.mock("axios");

function TestComponent() {
  const { authenticate, error } = useLogin();

  return (
    <div>
      <button
        onClick={() => authenticate("wrong@example.com", "wrongpassword")}
      >
        Login
      </button>
      {error && <span>{error}</span>}
    </div>
  );
}

describe("useLogin - Failed Authentication", () => {
  it("should handle a failed login", async () => {
    mockAxiosFailure();

    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    await act(async () => {
      screen.getByText("Login").click();
    });

    await waitFor(() => {
      expect(screen.getByText("Invalid email or password")).toBeInTheDocument();
    });
  });
});
