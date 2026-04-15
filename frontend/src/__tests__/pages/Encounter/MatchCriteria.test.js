/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

jest.mock("react-bootstrap", () => {
  const Modal = ({ show, onHide, children }) =>
    show ? (
      <div data-testid="modal">
        <button data-testid="modal-close" onClick={onHide}>
          x
        </button>
        {children}
      </div>
    ) : null;

  Modal.Header = ({ children }) => <div>{children}</div>;
  Modal.Title = ({ children }) => <h2>{children}</h2>;
  Modal.Body = ({ children }) => <div>{children}</div>;

  return { Modal };
});

jest.mock("../../../components/ContainerWithSpinner", () => (props) => (
  <div data-testid="container-spinner">{props.children}</div>
));

jest.mock("antd/es/tree-select", () => ({
  __esModule: true,
  default: (props) => (
    <div
      data-testid="tree-select"
      onClick={() => props.onChange(["loc1"], ["Label 1"], {})}
    >
      TreeSelect
    </div>
  ),
}));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid="select-input"
    value={props.value || ""}
    disabled={props.disabled}
    onChange={(e) => props.onChange && props.onChange(e.target.value)}
  >
    <option value="">--</option>
    {(props.options || []).map((o) => (
      <option key={o.value} value={o.value}>
        {o.label}
      </option>
    ))}
  </select>
));

jest.mock("../../../components/MainButton", () => (props) => (
  <button
    data-testid={props["data-testid"] || "main-button"}
    disabled={props.disabled}
    onClick={props.onClick}
  >
    {props.children}
  </button>
));

jest.mock("react-select", () => (props) => (
  <div
    data-testid="react-select"
    onClick={() =>
      props.onChange &&
      props.onChange([{ value: "algo1", label: "Algorithm 1" }])
    }
  >
    ReactSelect
  </div>
));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  return {
    __esModule: true,
    default: React.createContext({
      primaryColors: {
        primary700: "#123456",
      },
    }),
  };
});

import MatchCriteriaModal from "../../../pages/Encounter/MatchCriteria";

const makeStore = (overrides = {}) => ({
  siteSettingsLoading: false,
  locationIdOptions: [{ label: "L1", value: "loc1" }],
  newMatch: {
    locationId: [],
    owner: "",
    setOwner: jest.fn(),
    handleStrictChange: jest.fn(),
    algorithmOptions: [
      { label: "Algorithm 1", value: "algo1" },
      { label: "Algorithm 2", value: "algo2" },
    ],
    algorithms: [],
    annotationIds: ["ann-1"],
    setAlgorithm: jest.fn(),
    buildNewMatchPayload: jest
      .fn()
      .mockResolvedValue({ status: 200, data: { taskId: "t123" } }),
  },
  modals: {
    setOpenMatchCriteriaModal: jest.fn(),
  },
  ...overrides,
});

describe("MatchCriteriaModal", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    global.open = jest.fn();
    global.alert = jest.fn();
  });

  test("renders modal body, messages and inputs", async () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    expect(screen.getByTestId("modal")).toBeInTheDocument();
    expect(screen.getByText("MATCH_CRITERIA")).toBeInTheDocument();
    expect(screen.getByText("MATCH_DESC_1")).toBeInTheDocument();

    expect(await screen.findByTestId("tree-select")).toBeInTheDocument();

    expect(screen.getByTestId("select-input")).toBeInTheDocument();
    expect(screen.getByTestId("react-select")).toBeInTheDocument();

    const buttons = screen.getAllByTestId("main-button");
    expect(buttons).toHaveLength(2);

    expect(screen.getByText("MATCH")).toBeInTheDocument();
    expect(screen.getByText("CANCEL")).toBeInTheDocument();
  });

  test("clicking Cancel calls onClose", async () => {
    const store = makeStore();
    const onClose = jest.fn();

    render(
      <MatchCriteriaModal isOpen={true} store={store} onClose={onClose} />,
    );

    const cancelBtn = await screen.findByText("CANCEL");
    fireEvent.click(cancelBtn);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  test("tree select change calls store.newMatch.handleStrictChange", async () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(await screen.findByTestId("tree-select"));

    expect(store.newMatch.handleStrictChange).toHaveBeenCalledWith(
      ["loc1"],
      ["Label 1"],
      {},
    );
  });

  test("select input change calls setOwner", () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.change(screen.getByTestId("select-input"), {
      target: { value: "mydata" },
    });

    expect(store.newMatch.setOwner).toHaveBeenCalledWith("mydata");
  });

  test("react-select change maps options to values and calls setAlgorithm", () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(screen.getByTestId("react-select"));

    expect(store.newMatch.setAlgorithm).toHaveBeenCalledWith(["algo1"]);
  });

  test("successful match calls buildNewMatchPayload, opens window and closes modal", async () => {
    const base = makeStore();
    const store = makeStore({
      newMatch: {
        ...base.newMatch,
        algorithms: ["algo1"],
        annotationIds: ["ann-1"],
      },
    });

    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(await screen.findByText("MATCH"));

    await waitFor(() => {
      expect(store.newMatch.buildNewMatchPayload).toHaveBeenCalledTimes(1);
      expect(global.open).toHaveBeenCalledWith(
        "/iaResults.jsp?taskId=t123",
        "_blank",
      );
      expect(store.modals.setOpenMatchCriteriaModal).toHaveBeenCalledWith(
        false,
      );
    });
  });

  test("failed match shows alert", async () => {
    const base = makeStore();
    const store = makeStore({
      newMatch: {
        ...base.newMatch,
        algorithms: ["algo1"],
        annotationIds: ["ann-1"],
        buildNewMatchPayload: jest
          .fn()
          .mockResolvedValue({ status: 500, data: {} }),
      },
    });

    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(await screen.findByText("MATCH"));

    await waitFor(() => {
      expect(global.alert).toHaveBeenCalledWith(
        "There was an error creating the match. Please try again.",
      );
    });
  });

  test("does not call handlers when siteSettingsLoading is true", async () => {
    const store = makeStore({ siteSettingsLoading: true });

    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(await screen.findByTestId("tree-select"));
    fireEvent.click(screen.getByTestId("react-select"));

    expect(store.newMatch.handleStrictChange).not.toHaveBeenCalled();
    expect(store.newMatch.setAlgorithm).not.toHaveBeenCalled();

    expect(screen.getByText("MATCH").closest("button")).toBeDisabled();
  });
});
