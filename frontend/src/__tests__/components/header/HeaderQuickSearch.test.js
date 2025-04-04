import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import HeaderQuickSearch from "../../../components/header/HeaderQuickSearch";
import usePostHeaderQuickSearch from "../../../models/usePostHeaderQuickSearch";
import { IntlProvider } from "react-intl";
import "@testing-library/jest-dom";

jest.mock("../../../models/usePostHeaderQuickSearch");

describe("HeaderQuickSearch Component", () => {
  beforeEach(() => {
    usePostHeaderQuickSearch.mockReturnValue({
      searchResults: [],
      loading: false,
    });
  });

  test("renders search input field", () => {
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    expect(
      screen.getByPlaceholderText("Search Individuals"),
    ).toBeInTheDocument();
  });

  test("displays dropdown when input is focused", () => {
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    const input = screen.getByPlaceholderText("Search Individuals");
    fireEvent.focus(input);
    expect(screen.getByTestId("header-quick-search")).toBeInTheDocument();
  });

  test("clears search input when clear button is clicked", () => {
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    const input = screen.getByPlaceholderText("Search Individuals");
    fireEvent.change(input, { target: { value: "test" } });
    expect(input.value).toBe("test");
    const clearButton = screen.getByRole("button");
    fireEvent.click(clearButton);
    expect(input.value).toBe("");
  });

  test("shows loading spinner when searching", async () => {
    usePostHeaderQuickSearch.mockReturnValue({
      searchResults: [],
      loading: true,
    });
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    const input = screen.getByPlaceholderText("Search Individuals");
    await act(async () => {
      fireEvent.focus(input);
      fireEvent.change(input, { target: { value: "John" } });
    });

    await waitFor(() => {
      expect(screen.getByText("LOADING")).toBeInTheDocument();
    });
  });

  test("displays search results", async () => {
    usePostHeaderQuickSearch.mockReturnValue({
      searchResults: [{ id: "123", names: ["John Doe"], taxonomy: "Mammal" }],
      loading: false,
    });
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    const input = screen.getByPlaceholderText("Search Individuals");
    await act(async () => {
      fireEvent.focus(input);
      fireEvent.change(input, { target: { value: "John" } });
    });
    await waitFor(() => {
      expect(screen.getByText("John Doe")).toBeInTheDocument();
      expect(screen.getByText("Mammal")).toBeInTheDocument();
    });
  });

  test("opens new tab on search result click", async () => {
    window.open = jest.fn();
    usePostHeaderQuickSearch.mockReturnValue({
      searchResults: [{ id: "123", names: ["John Doe"], taxonomy: "Mammal" }],
      loading: false,
    });
    render(
      <IntlProvider locale="en">
        <HeaderQuickSearch />
      </IntlProvider>,
    );
    const input = screen.getByPlaceholderText("Search Individuals");
    await act(async () => {
      fireEvent.focus(input);
      fireEvent.change(input, { target: { value: "John" } });
    });
    const resultItem = screen.getByText("John Doe");
    fireEvent.mouseDown(resultItem);
    expect(window.open).toHaveBeenCalledWith("/individuals.jsp?id=123");
  });
});
