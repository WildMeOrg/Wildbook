/* eslint-disable react/display-name */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/generalInputs/TextInput", () => (props) => (
  <input
    data-testid="textinput-email"
    value={props.value || ""}
    placeholder={props.placeholder}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  />
));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid="selectinput-role"
    value={props.value || ""}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="" />
    {(props.options || []).map((opt) => (
      <option key={opt} value={opt}>
        {opt}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/MainButton", () => (props) => (
  <button
    data-testid={props["data-testid"] || "main-button"}
    onClick={props.onClick}
    disabled={props.disabled}
  >
    {props.children}
  </button>
));

import ThemeColorContext from "../../../ThemeColorProvider";
import { AddPeople } from "../../../pages/Encounter/AddPeople";

const ThemeWrapper = ({ children }) => (
  <ThemeColorContext.Provider
    value={{ primaryColors: { primary700: "#123456" } }}
  >
    {children}
  </ThemeColorContext.Provider>
);

const makeStore = (overrides = {}) => ({
  newPersonEmail: "",
  newPersonRole: "",
  setNewPersonName: jest.fn(),
  setNewPersonEmail: jest.fn(),
  setNewPersonRole: jest.fn(),
  addNewPerson: jest.fn(),
  refreshEncounterData: jest.fn(),
  modals: { setOpenAddPeopleModal: jest.fn() },
  ...overrides,
});

const renderAddPeople = (store) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <ThemeWrapper>
        <AddPeople store={store} />
      </ThemeWrapper>
    </IntlProvider>,
  );

describe("AddPeople (JS)", () => {
  test("renders header and buttons (FormattedMessage ids visible)", () => {
    const store = makeStore();
    renderAddPeople(store);

    expect(screen.getByText("ADD_NEW_PEOPLE")).toBeInTheDocument();
    expect(screen.getByText("SAVE")).toBeInTheDocument();
    expect(screen.getByText("CANCEL")).toBeInTheDocument();

    expect(screen.getByTestId("textinput-email")).toBeInTheDocument();
    expect(screen.getByTestId("selectinput-role")).toBeInTheDocument();
  });

  test("Save is disabled when email or role is missing", () => {
    const store = makeStore({ newPersonEmail: "", newPersonRole: "" });
    renderAddPeople(store);

    const saveBtn = screen.getByText("SAVE").closest("button");
    expect(saveBtn).toBeDisabled();
  });

  test("clicking Save triggers addNewPerson and refreshEncounterData when fields provided", async () => {
    const store = makeStore({
      newPersonEmail: "user@example.com",
      newPersonRole: "submitters",
    });
    renderAddPeople(store);

    const saveBtn = screen.getByText("SAVE").closest("button");
    expect(saveBtn).not.toBeDisabled();

    await userEvent.click(saveBtn);

    expect(store.addNewPerson).toHaveBeenCalledTimes(1);
    expect(store.refreshEncounterData).toHaveBeenCalledTimes(1);
  });

  test("clicking Cancel clears fields and closes modal", async () => {
    const store = makeStore({
      newPersonEmail: "u@x.com",
      newPersonRole: "informOthers",
    });
    renderAddPeople(store);

    const cancelBtn = screen.getByText("CANCEL").closest("button");
    await userEvent.click(cancelBtn);

    expect(store.setNewPersonName).toHaveBeenCalledWith("");
    expect(store.setNewPersonEmail).toHaveBeenCalledWith("");
    expect(store.setNewPersonRole).toHaveBeenCalledWith("");
    expect(store.modals.setOpenAddPeopleModal).toHaveBeenCalledWith(false);
  });
});
