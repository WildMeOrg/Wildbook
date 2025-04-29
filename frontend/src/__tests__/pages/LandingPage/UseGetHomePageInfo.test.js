import React from "react";
import { render, screen } from "@testing-library/react";
import useFetch from "../../../hooks/useFetch";

jest.mock("../../../hooks/useFetch");

describe("get home page info hook", () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  const TestComponent = () => {
    const { data, error, isLoading } = useFetch();
    return (
      <div>
        {isLoading && <div data-testid="loading">Loading...</div>}
        {error && <div data-testid="error">{error.message}</div>}
        {data && data.siteTitle && (
          <div data-testid="data">{data.siteTitle}</div>
        )}
      </div>
    );
  };

  test("should fetch site settings successfully", async () => {
    useFetch.mockReturnValue({
      data: { siteTitle: "Test Site" },
      error: null,
      isLoading: false,
    });

    render(<TestComponent />);
    screen.debug();
    expect(screen.getByTestId("data")).toBeInTheDocument();
  });

  test("should show loading state", async () => {
    useFetch.mockReturnValue({
      data: null,
      error: null,
      isLoading: true,
    });

    render(<TestComponent />);
    expect(screen.getByTestId("loading")).toBeInTheDocument();
    expect(screen.queryByTestId("data")).not.toBeInTheDocument();
    expect(screen.queryByTestId("error")).not.toBeInTheDocument();
  });

  test("should show error message", async () => {
    const mockError = new Error("Failed to fetch");
    useFetch.mockReturnValue({
      data: null,
      error: mockError,
      isLoading: false,
    });

    render(<TestComponent />);
    expect(screen.getByTestId("error")).toHaveTextContent("Failed to fetch");
    expect(screen.queryByTestId("data")).not.toBeInTheDocument();
    expect(screen.queryByTestId("loading")).not.toBeInTheDocument();
  });

  test("should not show data if data is null", async () => {
    useFetch.mockReturnValue({
      data: null,
      error: null,
      isLoading: false,
    });

    render(<TestComponent />);
    expect(screen.queryByTestId("data")).not.toBeInTheDocument();
  });

  test("should handle empty data object", async () => {
    useFetch.mockReturnValue({
      data: {},
      error: null,
      isLoading: false,
    });

    render(<TestComponent />);
    expect(screen.queryByTestId("data")).not.toBeInTheDocument();
  });
});
