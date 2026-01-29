/* eslint-disable react/display-name */
import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("react-intl", () => ({
  FormattedMessage: ({ id }) => <span>{id}</span>,
}));

jest.mock("react-bootstrap", () => ({
  Modal: ({ show, onHide, children }) =>
    show ? (
      <div data-testid="modal">
        <button data-testid="modal-close" onClick={onHide}>
          x
        </button>
        {children}
      </div>
    ) : null,
  ModalHeader: ({ children }) => <div>{children}</div>,
  ModalTitle: ({ children }) => <h2>{children}</h2>,
  ModalBody: ({ children }) => <div>{children}</div>,
}));

jest.mock("antd/es/tree-select", () => (props) => (
  <div
    data-testid="tree-select"
    onClick={() => props.onChange(["loc1"], ["Label 1"], {})}
  >
    TreeSelect
  </div>
));

jest.mock("../../../components/generalInputs/SelectInput", () => (props) => (
  <select
    data-testid="select-input"
    value={props.value || ""}
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

  test("renders modal body, messages and inputs", () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    expect(screen.getByTestId("modal")).toBeInTheDocument();
    expect(screen.getByText("MATCH_CRITERIA")).toBeInTheDocument();
    expect(screen.getByText("MATCH_DESC_1")).toBeInTheDocument();
    expect(screen.getByTestId("tree-select")).toBeInTheDocument();
    expect(screen.getByTestId("select-input")).toBeInTheDocument();
    expect(screen.getByTestId("react-select")).toBeInTheDocument();
    const buttons = screen.getAllByTestId("main-button");
    expect(buttons.length).toBe(2);
  });

  test("clicking Cancel calls onClose", () => {
    const store = makeStore();
    const onClose = jest.fn();
    render(
      <MatchCriteriaModal isOpen={true} store={store} onClose={onClose} />,
    );

    const cancelBtn = screen.getAllByTestId("main-button")[1];
    fireEvent.click(cancelBtn);

    expect(onClose).toHaveBeenCalled();
  });

  test("tree select change calls store.newMatch.handleStrictChange", () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    fireEvent.click(screen.getByTestId("tree-select"));

    expect(store.newMatch.handleStrictChange).toHaveBeenCalledWith(
      ["loc1"],
      ["Label 1"],
      {},
    );
  });

  test("select input change calls setOwner", () => {
    const store = makeStore();
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    const sel = screen.getByTestId("select-input");
    fireEvent.change(sel, { target: { value: "mydata" } });

    expect(store.newMatch.setOwner).toHaveBeenCalledWith("mydata");
  });

  test("match button is disabled when no algorithms or annotationIds", () => {
    const store = makeStore({
      newMatch: {
        ...makeStore().newMatch,
        algorithms: [],
        annotationIds: [],
      },
    });
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    const matchBtn = screen.getAllByTestId("main-button")[0];
    expect(matchBtn).toBeDisabled();
  });

  test("successful match calls buildNewMatchPayload, opens window and closes modal", async () => {
    const store = makeStore({
      newMatch: {
        ...makeStore().newMatch,
        algorithms: ["algo1"],
        annotationIds: ["ann-1"],
      },
    });
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    const matchBtn = screen.getAllByTestId("main-button")[0];
    fireEvent.click(matchBtn);

    await waitFor(() => {
      expect(store.newMatch.buildNewMatchPayload).toHaveBeenCalled();
      expect(global.open).toHaveBeenCalledWith(
        "/react/match-results?taskId=t123",
        "_blank",
      );
      expect(store.modals.setOpenMatchCriteriaModal).toHaveBeenCalledWith(
        false,
      );
    });
  });

  test("failed match shows alert", async () => {
    const store = makeStore({
      newMatch: {
        ...makeStore().newMatch,
        algorithms: ["algo1"],
        annotationIds: ["ann-1"],
        buildNewMatchPayload: jest
          .fn()
          .mockResolvedValue({ status: 500, data: {} }),
      },
    });
    render(<MatchCriteriaModal isOpen={true} store={store} />);

    const matchBtn = screen.getAllByTestId("main-button")[0];
    fireEvent.click(matchBtn);

    await waitFor(() => {
      expect(global.alert).toHaveBeenCalledWith(
        "There was an error creating the match. Please try again.",
      );
    });
  });
});
