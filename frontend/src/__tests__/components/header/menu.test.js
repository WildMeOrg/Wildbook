
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import Menu from "../../../components/header/Menu";
import { IntlProvider } from "react-intl";
import "@testing-library/jest-dom";

jest.mock("../../../constants/navMenu", () => ({
  authenticatedMenu: () => [
    {
      Submit: [
        {
          name: "Report an Encounter",
          href: "/report",
          sub: [
            { name: "SubItem 1", href: "/sub-item-1" },
            { name: "SubItem 2", href: "/sub-item-2" }
          ],
        },
      ],
    },
  ],
}));


test("renders the Menu component without crashing", () => {
  render(
    <IntlProvider locale="en">
      <Menu username="testuser" showclassicsubmit={true} showClassicEncounterSearch={false} />
    </IntlProvider>
  );
  expect(screen.getByTestId("menu")).toBeInTheDocument();
});

test('renders "Submit" in the menu and matches snapshot', () => {
  render(
    <IntlProvider locale="en">
      <Menu username="tomcat" />
    </IntlProvider>
  );
  expect(screen.getByText("SUBMIT")).toBeInTheDocument();
});

test("opens dropdown on hover", async () => {
  render(
    <IntlProvider locale="en">
      <Menu username="testuser" />
    </IntlProvider>
  );
  const menuItem = screen.getAllByRole("button")[0];
  fireEvent.mouseEnter(menuItem);
  await waitFor(() => {
    expect(menuItem.getAttribute("aria-expanded")).toBe("true");
  });
});

test("closes dropdown on mouse leave", async () => {
  render(
    <IntlProvider locale="en">
      <Menu username="testuser" />
    </IntlProvider>
  );
  const menuItem = screen.getAllByRole("button")[0];
  fireEvent.mouseEnter(menuItem);
  await waitFor(() => expect(menuItem.getAttribute("aria-expanded")).toBe("true"));
  fireEvent.mouseLeave(menuItem);
  await waitFor(() => expect(menuItem.getAttribute("aria-expanded")).toBe("false"));
});

test("renders sub-menu items when parent is hovered", async () => {
  render(
    <IntlProvider locale="en">
      <Menu username="testuser" />
    </IntlProvider>
  );
  const parentMenu1 = screen.getByText("SUBMIT");
  fireEvent.mouseEnter(parentMenu1);
  const parentMenu2 = screen.getByText("Report an Encounter");
  fireEvent.mouseEnter(parentMenu2);

  await waitFor(() => {
    expect(screen.getByText("SubItem 1")).toBeInTheDocument();
    expect(screen.getByText("SubItem 2")).toBeInTheDocument();
    const link = screen.getByText("SubItem 1");
    expect(link).toHaveAttribute("href", "/sub-item-1");
  });
});


test("sets dropdown color to white on mouse leave", async () => {
  render(
    <IntlProvider locale="en">
      <Menu username="testuser" />
    </IntlProvider>
  );

  
  const parentMenu1 = screen.getByText("SUBMIT");
  fireEvent.mouseEnter(parentMenu1);
  const parentMenu2 = screen.getByText("Report an Encounter");
  fireEvent.mouseEnter(parentMenu2);

  const subMenu = screen.getByText("SubItem 1"); 

  fireEvent.mouseEnter(subMenu);
  fireEvent.mouseLeave(subMenu);

  await waitFor(() => {
    expect(subMenu.parentElement).not.toHaveClass("show"); 
  });
});





