import React from "react";
import { screen } from "@testing-library/react";
import LoginPage from "../../../pages/Login";
import "@testing-library/jest-dom";
import { renderWithProviders } from "../../../utils/utils";
import { useSiteSettings } from "../../../SiteSettingsContext";

jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));

test("renders login page correctly", () => {
  useSiteSettings.mockReturnValue({
    data: {},
    isLoading: false,
    error: null,
  });
  renderWithProviders(<LoginPage />);

  expect(screen.getByPlaceholderText("Username")).toBeInTheDocument();
  expect(screen.getByPlaceholderText("Password")).toBeInTheDocument();
  expect(screen.getByRole("button", { type: "submit" })).toBeInTheDocument();
});
