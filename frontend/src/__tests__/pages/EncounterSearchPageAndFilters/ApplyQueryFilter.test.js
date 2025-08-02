import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import ApplyQueryFilter from "../../../components/filterFields/ApplyQueryFilter";
import { renderWithProviders } from "../../../utils/utils";

describe("ApplyQueryFilter Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    delete window.location;
    window.location = { href: jest.fn() };
  });

  const renderComponent = () => renderWithProviders(<ApplyQueryFilter />);

  it("renders without crashing", () => {
    renderComponent();
    expect(screen.getByText("APPLY_SEARCH_ID")).toBeInTheDocument();
    expect(screen.getByText("APPLY_SEARCH_ID_DESC")).toBeInTheDocument();
  });

  it("updates input field correctly", () => {
    renderComponent();
    const input = screen.getByPlaceholderText("Search ID");
    fireEvent.change(input, { target: { value: "12345" } });
    expect(input.value).toBe("12345");
  });

  it("redirects on Enter key press", () => {
    renderComponent();
    const input = screen.getByPlaceholderText("Search ID");
    fireEvent.change(input, { target: { value: "12345" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    expect(window.location.href).toBe("/encounter-search?searchQueryId=12345");
  });

  it("redirects on button click", () => {
    renderComponent();
    const input = screen.getByPlaceholderText("Search ID");
    const button = screen.getByText("APPLY");

    fireEvent.change(input, { target: { value: "12345" } });
    fireEvent.click(button);

    expect(window.location.href).toBe("/encounter-search?searchQueryId=12345");
  });

  it("does not redirect when input is empty", () => {
    renderComponent();
    const button = screen.getByText("APPLY");
    fireEvent.click(button);
    expect(window.location.href).not.toBe("/encounter-search?searchQueryId=");
  });
});
