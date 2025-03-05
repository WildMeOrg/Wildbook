import { render, screen, act, waitFor } from "@testing-library/react";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { mockAxiosFailure } from "../../../utils/utils";
import { MemoryRouter } from "react-router-dom";
import React, { useEffect } from "react";

jest.mock("axios");

function TestComponent() {
  const { authenticate, error } = useLogin();
  useEffect(() => {
    const fetchData = async () => {
      const result = await authenticate("wrong@example.com", "wrongpassword");
      console.log("11111111111", result);
    };

    fetchData();
  }, []);

  React.useEffect(() => {
    console.log("🔥 Error state updated:", error);
  }, [error]);

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

    // await act(async () => {
    //   screen.getByText("Login").click();
    // });

    // expect(screen.getByText("Invalid email or password")).toBeInTheDocument();

    await act(async () => {
      screen.getByText("Login").click();
    });

    await waitFor(() => {
      expect(screen.getByText("Invalid email or password")).toBeInTheDocument();
    });
  });
});
