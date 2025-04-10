import React from "react";
import { render } from "@testing-library/react";
import Menu from "../components/header/Menu";
import { IntlProvider } from "react-intl";
import "@testing-library/jest-dom";

jest.mock("../constants/navMenu", () => ({
  authenticatedMenu: () => [
    {
      submit: [{ name: "Submit", href: "/submit" }],
    },
    {
      learn: [{ name: "Learn", href: "/learn" }],
    },
  ],
}));

test('renders "Submit" in the menu and matches snapshot', () => {
  const { getByText } = render(
    <IntlProvider locale="en">
      <Menu username="tomcat" />
    </IntlProvider>,
  );

  expect(getByText("SUBMIT")).toBeInTheDocument();
  expect(getByText("SUBMIT")).toMatchSnapshot();
});
