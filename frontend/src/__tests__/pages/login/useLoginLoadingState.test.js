import { render, screen, act } from "@testing-library/react";
import useLogin from "../../../models/auth/useLogin";
import { MemoryRouter } from "react-router-dom";
import { wrapper } from "../../../utils/testWrapper";
import React from "react";

function TestComponent() {
  const { authenticate, loading } = useLogin();
  return (
    <div>
      <button onClick={() => authenticate("user@example.com", "password")}>
        Login
      </button>
      {loading && <span>Loading...</span>}
    </div>
  );
}

describe("useLogin - Loading State", () => {
  it("should set loading to true when login starts", async () => {
    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    expect(screen.queryByText("Loading...")).not.toBeInTheDocument();

    act(() => {
      screen.getByText("Login").click();
    });

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });
});
