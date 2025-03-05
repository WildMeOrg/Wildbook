import { render, screen, act } from "@testing-library/react";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { mockAxiosSuccess } from "../../../utils/utils";
import { MemoryRouter } from "react-router-dom";
import React from "react";

jest.mock("axios");

function TestComponent() {
  const { authenticate } = useLogin();
  return (
    <button onClick={() => authenticate("user@example.com", "password")}>
      Login
    </button>
  );
}

describe("useLogin - Successful Authentication", () => {
  it("should handle a successful login and redirect", async () => {
    mockAxiosSuccess({ success: true, redirectUrl: "/dashboard" });

    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    await act(async () => {
      screen.getByText("Login").click();
    });

    expect(window.location.href).toContain("/react");
  });
});
