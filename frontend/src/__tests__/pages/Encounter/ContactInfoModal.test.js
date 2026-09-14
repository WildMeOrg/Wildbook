/* eslint-disable react/display-name */
import React from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/MainButton", () => (props) => (
  <button data-testid="main-button" onClick={props.onClick}>
    {props.children}
  </button>
));

jest.mock("../../../pages/Encounter/ContactInfoCard", () => (props) => (
  <div data-testid={`contact-card-${props.title}`}>
    <span data-testid={`card-title-${props.title}`}>{props.title}</span>
    <span data-testid={`card-type-${props.title}`}>{props.type}</span>
    <span data-testid={`card-count-${props.title}`}>
      {(props.data || []).length}
    </span>
  </div>
));

jest.mock("../../../pages/Encounter/AddPeople", () => () => (
  <div data-testid="add-people" />
));

jest.mock("react-bootstrap", () => {
  const Modal = ({ show, onHide, children }) =>
    show ? (
      <div data-testid="modal">
        <button data-testid="modal-close" onClick={onHide}>
          ×
        </button>
        {children}
      </div>
    ) : null;

  Modal.Header = ({ children }) => (
    <div data-testid="modal-header">{children}</div>
  );
  Modal.Title = ({ children }) => (
    <div data-testid="modal-title">{children}</div>
  );
  Modal.Body = ({ children }) => <div data-testid="modal-body">{children}</div>;

  return { Modal };
});

import ThemeColorContext from "../../../ThemeColorProvider";
import ContactInfoModal from "../../../pages/Encounter/ContactInfoModal";

const ThemeWrapper = ({ children }) => (
  <ThemeColorContext.Provider
    value={{ primaryColors: { primary700: "#123456" } }}
  >
    {children}
  </ThemeColorContext.Provider>
);

const renderModal = (ui) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <ThemeWrapper>{ui}</ThemeWrapper>
    </IntlProvider>,
  );

const makeStore = (overrides = {}) => ({
  access: "write",
  encounterData: {
    submitterInfo: {},
    submitters: [],
    photographers: [],
    informOthers: [],
  },
  modals: {
    setOpenAddPeopleModal: jest.fn(),
    openAddPeopleModal: false,
  },
  ...overrides,
});

describe("ContactInfoModal", () => {
  test("returns null when isOpen=false", () => {
    const store = makeStore();
    renderModal(
      <ContactInfoModal isOpen={false} onClose={jest.fn()} store={store} />,
    );

    expect(screen.queryByTestId("modal")).toBeNull();
  });

  test("renders title and conditional ContactInfoCard blocks", () => {
    const store = makeStore({
      encounterData: {
        submitterInfo: { id: "m1", displayName: "Mgr" },
        submitters: [{ id: "s1" }],
        photographers: [],
        informOthers: [{ id: "i1" }, { id: "i2" }],
      },
    });

    renderModal(
      <ContactInfoModal isOpen={true} onClose={jest.fn()} store={store} />,
    );

    expect(screen.getByText("CONTACT_INFORMATION")).toBeInTheDocument();

    expect(
      screen.getByTestId("contact-card-MANAGING_RESEARCHER"),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId("card-count-MANAGING_RESEARCHER"),
    ).toHaveTextContent("1");

    expect(screen.getByTestId("contact-card-SUBMITTER")).toBeInTheDocument();
    expect(screen.getByTestId("card-count-SUBMITTER")).toHaveTextContent("1");

    expect(screen.queryByTestId("contact-card-PHOTOGRAPHER")).toBeNull();

    expect(
      screen.getByTestId("contact-card-INFORM_OTHERS"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("card-count-INFORM_OTHERS")).toHaveTextContent(
      "2",
    );
  });

  test("clicking ADD_PEOPLE calls store.modals.setOpenAddPeopleModal(true)", async () => {
    const user = userEvent.setup();
    const store = makeStore();

    renderModal(
      <ContactInfoModal isOpen={true} onClose={jest.fn()} store={store} />,
    );

    await user.click(screen.getByTestId("main-button"));
    expect(store.modals.setOpenAddPeopleModal).toHaveBeenCalledWith(true);
  });

  test("renders AddPeople when openAddPeopleModal=true and access=write", () => {
    const store = makeStore({
      access: "write",
      modals: {
        setOpenAddPeopleModal: jest.fn(),
        openAddPeopleModal: true,
      },
    });

    renderModal(
      <ContactInfoModal isOpen={true} onClose={jest.fn()} store={store} />,
    );

    expect(screen.getByTestId("add-people")).toBeInTheDocument();
  });

  test("does not render AddPeople when access is not write", () => {
    const store = makeStore({
      access: "read",
      modals: {
        setOpenAddPeopleModal: jest.fn(),
        openAddPeopleModal: true,
      },
    });

    renderModal(
      <ContactInfoModal isOpen={true} onClose={jest.fn()} store={store} />,
    );

    expect(screen.queryByTestId("add-people")).toBeNull();
    expect(screen.queryByTestId("main-button")).toBeNull();
  });

  test("invokes onClose when modal close is triggered", async () => {
    const user = userEvent.setup();
    const onClose = jest.fn();
    const store = makeStore();

    renderModal(
      <ContactInfoModal isOpen={true} onClose={onClose} store={store} />,
    );

    await user.click(screen.getByTestId("modal-close"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
