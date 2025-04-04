import { render, screen, act, waitFor } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import React from "react";
import axios from "axios";

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

beforeEach(() => {
  delete window.location;
  window.location = {
    href: "",
    assign: jest.fn((url) => {
      window.location.href = url;
    }),
  };
});

describe("useLogin - Successful Authentication", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("should handle a successful login and redirect to default home", async () => {
    useLocation.mockReturnValue({ search: "", hash: "" });
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

  it("should handle a successful login and redirect to provided URL", async () => {
    useLocation.mockReturnValue({ search: "?redirect=%2Fdashboard", hash: "" });

    axios.request.mockResolvedValue({
      data: {
        success: true,
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

    expect(window.location.href).toContain(
      `${process.env.PUBLIC_URL}/dashboard`,
    );
  });

  it("should use backend redirectUrl if provided", async () => {
    useLocation.mockReturnValue({ search: "?redirect=%2Fdashboard", hash: "" });
    axios.request.mockResolvedValue({
      data: {
        success: true,
        redirectUrl: "/admin",
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

    expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/admin`);
  });

  it("should prevent open redirect to external sites", async () => {
    useLocation.mockReturnValue({
      search: "?redirect=https%3A%2F%2Fmalicious.com",
      hash: "",
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

    expect(window.location.href).toContain(`${process.env.PUBLIC_URL}`);
  });
});
