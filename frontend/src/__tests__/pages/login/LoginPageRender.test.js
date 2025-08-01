import React from "react";
import { screen } from "@testing-library/react";
import LoginPage from "../../../pages/Login";
import "@testing-library/jest-dom";
import { renderWithProviders } from "../../../utils/utils";

test("renders login page correctly", () => {
  renderWithProviders(<LoginPage />);

  expect(screen.getByPlaceholderText("Username")).toBeInTheDocument();
  expect(screen.getByPlaceholderText("Password")).toBeInTheDocument();
  expect(screen.getByRole("button", { type: "submit" })).toBeInTheDocument();
});
