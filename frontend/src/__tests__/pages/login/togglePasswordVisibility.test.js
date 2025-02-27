import React from "react";
import { fireEvent } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import LoginPage from "../../../pages/Login";
import { screen, act } from "@testing-library/react";
import "@testing-library/jest-dom";

test("allows user to type username and password", async () => {
    renderWithProviders(<LoginPage />);

    const passwordInput = screen.getByPlaceholderText("Password");
    const toggleButton = screen.getByTestId("password-toggle");

    expect(passwordInput.type).toBe("password");

    await act(async () => {
        fireEvent.click(toggleButton);
    });

    expect(passwordInput.type).toBe("text");

    await act(async () => {
        fireEvent.click(toggleButton);
    });
    expect(passwordInput.type).toBe("password");
});
