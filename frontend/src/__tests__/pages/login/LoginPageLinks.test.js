import React from "react";
import { renderWithProviders } from "../../../utils/utils";
import LoginPage from "../../../pages/Login";
import { screen } from "@testing-library/react";

describe("LoginPage - Links", () => {
  test('renders "Forgot Password" link with correct href', () => {
    renderWithProviders(<LoginPage />);

    const forgotPasswordLink = screen.getByText(/forgot password/i);
    expect(forgotPasswordLink).toBeInTheDocument();
    expect(forgotPasswordLink).toHaveAttribute("href", "/resetPassword.jsp");
  });

  test('renders "Request Account" link with correct href', () => {
    renderWithProviders(<LoginPage />);

    const requestAccountLink = screen.getByText(/request account/i);
    expect(requestAccountLink).toBeInTheDocument();
    expect(requestAccountLink).toHaveAttribute(
      "href",
      "https://us7.list-manage.com/contact-form?u=c5af097df0ca8712f52ea1768&form_id=335cfeba915bbb2a6058d6ba705598ce",
    );
  });
});
