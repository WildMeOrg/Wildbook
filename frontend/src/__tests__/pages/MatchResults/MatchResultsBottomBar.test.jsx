import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchResultsBottomBar from "../../../pages/MatchResultsPage/components/MatchResultsBottomBar";

jest.mock("../../../components/MainButton", () => {
  const React = require("react");
  function MainButton({ children, onClick, disabled }) {
    return React.createElement("button", { onClick, disabled }, children);
  }
  MainButton.displayName = "MainButton";
  return MainButton;
});

jest.mock(
  "../../../pages/MatchResultsPage/components/CreateNewIndividualModal",
  () => {
    const React = require("react");
    function CreateNewIndividualModal({ show, onHide, onConfirm }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "create-individual-modal" },
        React.createElement(
          "button",
          { onClick: () => onConfirm(""), "data-testid": "modal-confirm" },
          "Confirm",
        ),
        React.createElement(
          "button",
          { onClick: onHide, "data-testid": "modal-cancel" },
          "Cancel",
        ),
      );
    }
    CreateNewIndividualModal.displayName = "CreateNewIndividualModal";
    return CreateNewIndividualModal;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/NewIndividualCreatedModal",
  () => {
    const React = require("react");
    function NewIndividualCreatedModal({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "new-individual-created-modal" },
        React.createElement("button", { onClick: onHide }, "Close"),
      );
    }
    NewIndividualCreatedModal.displayName = "NewIndividualCreatedModal";
    return NewIndividualCreatedModal;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchConfirmedModal",
  () => {
    const React = require("react");
    function MatchConfirmedModal({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "match-confirmed-modal" },
        React.createElement("button", { onClick: onHide }, "Close"),
      );
    }
    MatchConfirmedModal.displayName = "MatchConfirmedModal";
    return MatchConfirmedModal;
  },
);

// ---------------------------------------------------------------------------

const themeColor = {
  primaryColors: {
    primary50: "#E5F6FF",
    primary500: "#00ACCE",
    primary700: "#007599",
  },
};

const makeStore = (overrides = {}) => ({
  matchingState: "no_individuals",
  encounterId: "enc-001",
  encounterLocationId: "loc-1",
  individualId: null,
  individualDisplayName: null,
  newIndividualName: "",
  matchRequestLoading: false,
  matchRequestError: null,
  selectedMatch: [],
  selectedIncludingQuery: [{ encounterId: "enc-001", individualId: null }],
  setNewIndividualName: jest.fn(),
  handleCreateNewIndividual: jest.fn().mockResolvedValue({ ok: true }),
  handleMatch: jest.fn().mockResolvedValue({ success: true }),
  handleMerge: jest.fn().mockResolvedValue({ ok: true }),
  ...overrides,
});

const renderBar = (storeOverrides = {}) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MatchResultsBottomBar
        store={makeStore(storeOverrides)}
        themeColor={themeColor}
        identificationRemarks={["AI", "Manual"]}
      />
    </IntlProvider>,
  );

// ---------------------------------------------------------------------------

describe("MatchResultsBottomBar — no_individuals state", () => {
  test("renders MARK_AS_NEW_INDIVIDUAL button", () => {
    renderBar({ matchingState: "no_individuals" });
    expect(screen.getByText("MARK_AS_NEW_INDIVIDUAL")).toBeInTheDocument();
  });

  test("shows SET_MATCH_FOR message", () => {
    renderBar({ matchingState: "no_individuals" });
    expect(document.body).toHaveTextContent("SET_MATCH_FOR");
  });

  test("shows individual display name link when available", () => {
    renderBar({
      matchingState: "no_individuals",
      individualDisplayName: "Luna",
      individualId: "ind-1",
    });
    expect(screen.getByText("Luna")).toBeInTheDocument();
  });

  test("clicking MARK_AS_NEW_INDIVIDUAL opens CreateNewIndividualModal", () => {
    renderBar({ matchingState: "no_individuals" });
    fireEvent.click(screen.getByText("MARK_AS_NEW_INDIVIDUAL"));
    expect(screen.getByTestId("create-individual-modal")).toBeInTheDocument();
  });

  test("cancelling modal hides it", () => {
    renderBar({ matchingState: "no_individuals" });
    fireEvent.click(screen.getByText("MARK_AS_NEW_INDIVIDUAL"));
    fireEvent.click(screen.getByTestId("modal-cancel"));
    expect(
      screen.queryByTestId("create-individual-modal"),
    ).not.toBeInTheDocument();
  });

  test("successful confirm shows NewIndividualCreatedModal", async () => {
    renderBar({ matchingState: "no_individuals" });
    fireEvent.click(screen.getByText("MARK_AS_NEW_INDIVIDUAL"));
    fireEvent.click(screen.getByTestId("modal-confirm"));
    expect(
      await screen.findByTestId("new-individual-created-modal"),
    ).toBeInTheDocument();
  });
});

describe("MatchResultsBottomBar — single_individual state", () => {
  const singleStore = {
    matchingState: "single_individual",
    encounterId: "enc-001",
    individualId: "ind-001",
    individualDisplayName: "Willy",
    selectedIncludingQuery: [
      {
        encounterId: "enc-001",
        individualId: "ind-001",
        individualDisplayName: "Willy",
      },
    ],
  };

  test("renders CONFIRM_MATCH button", () => {
    renderBar(singleStore);
    expect(screen.getByText("CONFIRM_MATCH")).toBeInTheDocument();
  });

  test("renders MERGE_INDIVIDUAL message", () => {
    renderBar(singleStore);
    expect(screen.getByText("MERGE_INDIVIDUAL")).toBeInTheDocument();
  });

  test("clicking CONFIRM_MATCH calls store.handleMatch", async () => {
    const handleMatch = jest.fn().mockResolvedValue({ data: {} });
    renderBar({ ...singleStore, handleMatch });
    fireEvent.click(screen.getByText("CONFIRM_MATCH"));
    await waitFor(() => expect(handleMatch).toHaveBeenCalled());
  });

  test("shows MatchConfirmedModal after successful match", async () => {
    renderBar(singleStore);
    fireEvent.click(screen.getByText("CONFIRM_MATCH"));
    expect(
      await screen.findByTestId("match-confirmed-modal"),
    ).toBeInTheDocument();
  });
});

describe("MatchResultsBottomBar — two_individuals state", () => {
  const twoStore = {
    matchingState: "two_individuals",
    encounterId: "enc-001",
    individualId: "ind-001",
    individualDisplayName: "Willy",
    selectedIncludingQuery: [
      {
        encounterId: "enc-001",
        individualId: "ind-001",
        individualDisplayName: "Willy",
      },
      {
        encounterId: "enc-002",
        individualId: "ind-002",
        individualDisplayName: "Nemo",
      },
    ],
    handleMerge: jest.fn().mockResolvedValue({ ok: true }),
  };

  test("renders MERGE_INDIVIDUALS button", () => {
    renderBar(twoStore);
    expect(screen.getByText("MERGE_INDIVIDUALS")).toBeInTheDocument();
  });

  test("renders MERGE message with individual names", () => {
    renderBar(twoStore);
    expect(document.body).toHaveTextContent("MERGE");
    expect(screen.getByText("Willy")).toBeInTheDocument();
  });
});

describe("MatchResultsBottomBar — too_many_individuals state", () => {
  test("shows CANNOT_MERGE_MORE_THAN_TWO alert", () => {
    renderBar({ matchingState: "too_many_individuals" });
    expect(screen.getByText("CANNOT_MERGE_MORE_THAN_TWO")).toBeInTheDocument();
  });

  test("does not render action buttons", () => {
    renderBar({ matchingState: "too_many_individuals" });
    expect(screen.queryByText("CONFIRM_MATCH")).not.toBeInTheDocument();
    expect(screen.queryByText("MERGE_INDIVIDUALS")).not.toBeInTheDocument();
  });
});

describe("MatchResultsBottomBar — no_further_action_needed state", () => {
  test("shows NO_FURTHER_ACTION_NEEDED when a match is selected", () => {
    renderBar({
      matchingState: "no_further_action_needed",
      selectedMatch: [
        { key: "k1", encounterId: "enc-2", individualId: "ind-1" },
      ],
    });
    expect(screen.getByText("NO_FURTHER_ACTION_NEEDED")).toBeInTheDocument();
  });

  test("shows SET_MATCH_FOR when no match is selected", () => {
    renderBar({
      matchingState: "no_further_action_needed",
      selectedMatch: [],
    });
    expect(screen.getByText("SET_MATCH_FOR")).toBeInTheDocument();
  });
});

describe("MatchResultsBottomBar — Cancel button", () => {
  test("renders CANCEL button", () => {
    renderBar();
    expect(screen.getByText("CANCEL")).toBeInTheDocument();
  });

  test("CANCEL button calls window.close", () => {
    window.close = jest.fn();
    renderBar();
    fireEvent.click(screen.getByText("CANCEL"));
    expect(window.close).toHaveBeenCalled();
  });
});
