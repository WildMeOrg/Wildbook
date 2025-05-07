import React from "react";
import { render, fireEvent, act } from "@testing-library/react";
import MultiLanguageDropdown from "../../../components/navBar/MultiLanguageDropdown";
import LocaleContext from "../../../IntlProvider";
import { localeMap, languageMap } from "../../../constants/locales";
import "@testing-library/jest-dom";
import Cookies from "js-cookie";

beforeAll(() => {
  process.env.PUBLIC_URL = "/react";
});

jest.mock("js-cookie", () => ({
  get: jest.fn(),
}));

const mockOnLocaleChange = jest.fn();
Cookies.get.mockReturnValue("en");

test("renders MultiLanguageDropdown and changes language on click", async () => {

  const { getByAltText } = render(
    <LocaleContext.Provider value={{ onLocaleChange: mockOnLocaleChange }}>
      <MultiLanguageDropdown />
    </LocaleContext.Provider>,
  );

  expect(getByAltText("flag")).toBeInTheDocument();
  expect(getByAltText("flag").src).toContain("/react/flags/en.png");

});

test("renders changes language on click", async () => {
  const { getByRole, getByText, getByAltText } = render(
    <LocaleContext.Provider value={{ onLocaleChange: mockOnLocaleChange }}>
      <MultiLanguageDropdown />
    </LocaleContext.Provider>,
  );

  await act(async () => {
    fireEvent.click(getByRole("button"));
  });

  await act(async () => {
    fireEvent.click(getByText(languageMap["fr"]));
  });

  expect(getByAltText("fr").src).toContain(
    `/react/flags/${localeMap["fr"]}.png`,
  );
  expect(mockOnLocaleChange).toHaveBeenCalledWith("fr");
});