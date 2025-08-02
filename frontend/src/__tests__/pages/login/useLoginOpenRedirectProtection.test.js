import { render, screen, act, waitFor } from "@testing-library/react";
import { useLocation } from "react-router-dom";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { MemoryRouter } from "react-router-dom";
import React from "react";
import axios from "axios";

jest.mock("axios");
jest.mock("react-router-dom", () => ({
  ...jest.requireActual("react-router-dom"),
  useLocation: jest.fn(),
}));

beforeEach(() => {
  delete window.location;
  window.location = {
    href: "",
    assign: jest.fn((url) => {
      window.location.href = url;
    }),
  };
});

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

    axios.request.mockResolvedValue({
      data: {
        success: true,
        redirectUrl: null,
      },
    });

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
      expect(window.location.href).toBe(`${process.env.PUBLIC_URL}/home`);
    });
  });
});
