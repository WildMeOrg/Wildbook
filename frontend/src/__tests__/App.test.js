import React from "react";
import { render, screen } from "@testing-library/react";
import App from "../App";
import Cookies from "js-cookie";

jest.mock("../FrontDesk", () => {
  const mockComponent = () => <div data-testid="frontdesk">FrontDesk</div>;
  mockComponent.dispayName = "FrontDesk";
  return mockComponent;
});

beforeEach(() => {
  Cookies.remove("wildbookLangCode");
});
afterEach(() => {
  jest.clearAllMocks();
});

const OLD_ENV = process.env;
beforeEach(() => {
  jest.resetModules();
  process.env = { ...OLD_ENV };
});
afterAll(() => {
  process.env = OLD_ENV;
});

describe("App component", () => {
  test("renders without crashing using default 'en' locale", () => {
    render(<App />);
    expect(screen.getByTestId("frontdesk")).toBeInTheDocument();
  });

  test("uses locale from cookie", () => {
    Cookies.set("wildbookLangCode", "fr");
    render(<App />);
    expect(screen.getByTestId("frontdesk")).toBeInTheDocument();
  });

  test("handles PUBLIC_URL fallback", () => {
    delete process.env.PUBLIC_URL;
    render(<App />);
    expect(screen.getByTestId("frontdesk")).toBeInTheDocument();
  });
});
