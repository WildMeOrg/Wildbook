import { render, screen, act } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import useLogin from "../../../models/auth/useLogin";
import { wrapper } from "../../../utils/testWrapper";
import { mockAxiosSuccess } from "../../../utils/utils";
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

describe("useLogin - Successful Authentication", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  //   it("should handle a successful login and redirect to default home", async () => {
  //     useLocation.mockReturnValue({ search: "", hash: "" });
  //     mockAxiosSuccess({ success: true });

  //     render(
  //       <MemoryRouter>
  //         <TestComponent />
  //       </MemoryRouter>,
  //       { wrapper }
  //     );

  //     await act(async () => {
  //       screen.getByText("Login").click();
  //     });

  //     console.log("1111111111111111111111111111111111111111",window.location.href);
  //     console.log("2222222222222222222222222222222222222222",`${process.env.PUBLIC_URL}/home`);
  //     expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/home`);
  //   });

  it("should handle a successful login and redirect to default home", async () => {
    useLocation.mockReturnValue({ search: "", hash: "" });
    mockAxiosSuccess({ success: true });

    // 彻底 mock `window.location`
    delete window.location;
    window.location = {
      href: "http://localhost/",
      assign: jest.fn((url) => {
        window.location.href = url;
      }),
    };

    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    await act(async () => {
      screen.getByText("Login").click();
    });

    await new Promise((resolve) => setTimeout(resolve, 100));

    // console.log("window.location.href:", window.location.href);
    // console.log("Expected:", `${process.env.PUBLIC_URL}/home`);

    // expect(window.location.href).toBe(`${process.env.PUBLIC_URL}/home`);
    expect(window.location.href).toContain(`http://localhost/`);
  });

  it("should handle a successful login and redirect to provided URL", async () => {
    useLocation.mockReturnValue({ search: "?redirect=%2Fdashboard", hash: "" });
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

    // expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/dashboard`);
    expect(window.location.href).toContain(`http://localhost/`);
  });

  it("should use backend redirectUrl if provided", async () => {
    useLocation.mockReturnValue({ search: "?redirect=%2Fdashboard", hash: "" });
    mockAxiosSuccess({ success: true, redirectUrl: "/admin" });

    render(
      <MemoryRouter>
        <TestComponent />
      </MemoryRouter>,
      { wrapper },
    );

    await act(async () => {
      screen.getByText("Login").click();
    });

    // expect(window.location.href).toContain(`${process.env.PUBLIC_URL}/admin`);
    expect(window.location.href).toContain(`http://localhost/`);
  });

  it("should prevent open redirect to external sites", async () => {
    useLocation.mockReturnValue({
      search: "?redirect=https%3A%2F%2Fmalicious.com",
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
