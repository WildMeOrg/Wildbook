/* eslint-disable react/display-name */

import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/icons/TrashCanIcon", () => () => (
  <span data-testid="trash-icon" />
));

import ContactInfoCard from "../../../pages/Encounter/ContactInfoCard";

const makeStore = (overrides = {}) => ({
  access: "write",
  removeContact: jest.fn(),
  refreshEncounterData: jest.fn(),
  ...overrides,
});

describe("ContactInfoCard", () => {
  test("renders title via FormattedMessage id and contact items (write access)", () => {
    const store = makeStore();
    const data = [
      { id: "1", displayName: "Alice", image: "http://img/alice.png" },
      { id: "2", displayName: "Bob" },
    ];

    const { container } = render(
      <IntlProvider locale="en" messages={{}}>
        <ContactInfoCard
          title="SUBMITTERS"
          type="submitters"
          data={data}
          store={store}
        />
      </IntlProvider>,
    );

    expect(container.querySelector("#SUBMITTERS")).toBeInTheDocument();
    expect(screen.getByText("SUBMITTERS")).toBeInTheDocument();

    expect(screen.getByText("Alice")).toBeInTheDocument();
    expect(screen.getByText("Bob")).toBeInTheDocument();

    const img = screen.getByAltText("Avatar");
    expect(img).toBeInTheDocument();
    expect(img).toHaveAttribute("src", "http://img/alice.png");

    expect(container.querySelectorAll("i.bi.bi-person-circle").length).toBe(1);

    expect(screen.getAllByTestId("trash-icon")).toHaveLength(2);
  });

  test("clicking trash with confirm=true calls removeContact(type,id) and refreshEncounterData", async () => {
    const user = userEvent.setup();
    const store = makeStore({ access: "write" });
    const data = [
      { id: "1", displayName: "Alice", image: "http://img/alice.png" },
      { id: "2", displayName: "Bob" },
    ];

    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(true);

    render(
      <IntlProvider locale="en" messages={{}}>
        <ContactInfoCard
          title="SUBMITTERS"
          type="photographers"
          data={data}
          store={store}
        />
      </IntlProvider>,
    );

    const trashIcons = screen.getAllByTestId("trash-icon");
    await user.click(trashIcons[0]);

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(store.removeContact).toHaveBeenCalledWith("photographers", "1");
    expect(store.refreshEncounterData).toHaveBeenCalledTimes(1);

    confirmSpy.mockRestore();
  });

  test("clicking trash with confirm=false does not call store methods", async () => {
    const user = userEvent.setup();
    const store = makeStore({ access: "write" });
    const data = [{ id: "1", displayName: "Alice" }];

    const confirmSpy = jest.spyOn(window, "confirm").mockReturnValue(false);

    render(
      <IntlProvider locale="en" messages={{}}>
        <ContactInfoCard
          title="INFORM_OTHERS"
          type="informOthers"
          data={data}
          store={store}
        />
      </IntlProvider>,
    );

    await user.click(screen.getByTestId("trash-icon"));

    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(store.removeContact).not.toHaveBeenCalled();
    expect(store.refreshEncounterData).not.toHaveBeenCalled();

    confirmSpy.mockRestore();
  });
});
