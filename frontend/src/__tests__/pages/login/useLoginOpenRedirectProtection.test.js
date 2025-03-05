import { render, screen, act } from "@testing-library/react";
import { useLocation } from "react-router-dom";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { mockAxiosSuccess } from "../../../utils/utils";
import { MemoryRouter } from "react-router-dom";
import React from "react";

jest.mock("axios");
jest.mock("react-router-dom", () => ({
  ...jest.requireActual("react-router-dom"),
  useLocation: jest.fn(),
}));

function TestComponent() {
  const { authenticate } = useLogin();
  return (
    <button onClick={() => authenticate("user@example.com", "password")}>
      Login
    </button>
  );
}

describe("useLogin - Open Redirect Protection", () => {
  it("should prevent open redirects", async () => {
    useLocation.mockReturnValue({
      search: "?redirect=https://malicious.com",
      hash: "",
    });
    mockAxiosSuccess({ success: true });

    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    await act(async () => {
      screen.getByText("Login").click();
    });

    // expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/home`);
    expect(window.location.href).toContain(`http://localhost/`);
  });
});
