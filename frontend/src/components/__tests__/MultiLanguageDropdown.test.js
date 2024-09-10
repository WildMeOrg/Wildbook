import React from "react";
import { render, fireEvent } from "@testing-library/react";
import MultiLanguageDropdown from "../navBar/MultiLanguageDropdown";
import LocaleContext from "../../IntlProvider";
import { localeMap, languageMap } from "../../constants/locales";
import "@testing-library/jest-dom"; // Ensure you have this installed for extended matchers
import Cookies from "js-cookie";

jest.mock("js-cookie", () => ({
  get: jest.fn(),
}));

test("renders MultiLanguageDropdown and changes language on click", () => {
  const mockOnLocaleChange = jest.fn();
  Cookies.get.mockReturnValue("en");

  const { getByRole, getByText, getByAltText } = render(
    <LocaleContext.Provider value={{ onLocaleChange: mockOnLocaleChange }}>
      <MultiLanguageDropdown />
    </LocaleContext.Provider>,
  );

  expect(getByAltText("flag")).toBeInTheDocument();
  expect(getByAltText("flag").src).toContain("/react/flags/en.png");

  fireEvent.click(getByRole("button"));

  fireEvent.click(getByText(languageMap["fr"]));

  expect(getByAltText("fr").src).toContain(
    `/react/flags/${localeMap["fr"]}.png`,
  );
  expect(mockOnLocaleChange).toHaveBeenCalledWith("fr");
  expect(getByText(languageMap["fr"])).toMatchSnapshot();
});
