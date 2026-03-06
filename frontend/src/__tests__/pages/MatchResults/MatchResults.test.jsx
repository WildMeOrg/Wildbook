import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
  within,
} from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { IntlProvider } from "react-intl";
import axios from "axios";
import MatchResults from "../../../pages/MatchResultsPage/MatchResults";

jest.mock("axios");

jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: () => ({
    projectsForUser: {
      "proj-1": { name: "Project Alpha", prefix: "PA" },
      "proj-2": { name: "Project Beta", prefix: "PB" },
    },
    identificationRemarks: ["Confirmed", "Uncertain"],
  }),
}));

jest.mock("../../../components/FullScreenLoader", () => {
  const React = require("react");
  function FullScreenLoader() {
    return React.createElement("div", { "data-testid": "full-screen-loader" });
  }
  FullScreenLoader.displayName = "FullScreenLoader";
  return FullScreenLoader;
});

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchProspectTable",
  () => {
    const React = require("react");
    function MatchProspectTable({ taskId, columns, onToggleSelected }) {
      return React.createElement(
        "div",
        { "data-testid": "prospect-table-" + taskId },
        (columns || []).flat().map(function (col, i) {
          return React.createElement(
            "button",
            {
              key: i,
              "data-testid": "prospect-row",
              onClick: function () {
                if (onToggleSelected) {
                  onToggleSelected(
                    true,
                    "key-" + i,
                    "enc-" + i,
                    "ind-" + i,
                    "Name" + i,
                  );
                }
              },
            },
            "row-" + i,
          );
        }),
      );
    }
    MatchProspectTable.displayName = "MatchProspectTable";
    return MatchProspectTable;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchResultsBottomBar",
  () => {
    const React = require("react");
    function MatchResultsBottomBar() {
      return React.createElement("div", { "data-testid": "bottom-bar" });
    }
    MatchResultsBottomBar.displayName = "MatchResultsBottomBar";
    return MatchResultsBottomBar;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/InstructionsModal",
  () => {
    const React = require("react");
    function InstructionsModal({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "instructions-modal" },
        React.createElement(
          "button",
          { onClick: onHide, "data-testid": "close-instructions" },
          "Close",
        ),
      );
    }
    InstructionsModal.displayName = "InstructionsModal";
    return InstructionsModal;
  },
);

jest.mock(
  "../../../pages/MatchResultsPage/components/MatchCriteriaDrawer",
  () => {
    const React = require("react");
    function MatchCriteriaDrawer({ show, onHide }) {
      if (!show) return null;
      return React.createElement(
        "div",
        { "data-testid": "match-criteria-drawer" },
        React.createElement(
          "button",
          { onClick: onHide, "data-testid": "close-drawer" },
          "Close",
        ),
      );
    }
    MatchCriteriaDrawer.displayName = "MatchCriteriaDrawer";
    return MatchCriteriaDrawer;
  },
);

jest.mock("../../../components/MultiSelectWithCheckbox", () => {
  const React = require("react");
  function MultiSelectWithCheckbox({
    options,
    value,
    onChangeCommitted,
    placeholder,
  }) {
    return React.createElement(
      "select",
      {
        "data-testid": "project-multiselect",
        value: value[0] || "",
        onChange: function (e) {
          onChangeCommitted([e.target.value]);
        },
      },
      React.createElement("option", { value: "" }, placeholder),
      (options || []).map(function (o) {
        return React.createElement(
          "option",
          { key: o.value, value: o.value },
          o.label,
        );
      }),
    );
  }
  MultiSelectWithCheckbox.displayName = "MultiSelectWithCheckbox";
  return MultiSelectWithCheckbox;
});

// ---------------------------------------------------------------------------

const makeApiResponse = () => ({
  matchResultsRoot: {
    id: "task-1",
    status: "complete",
    statusOverall: "complete",
    dateCreated: "2024-06-01",
    method: { name: "hotspotter", description: "HotSpotter" },
    matchingSetFilter: {},
    matchResults: {
      numberCandidates: 10,
      queryAnnotation: {
        x: 0.1,
        y: 0.2,
        width: 0.3,
        height: 0.4,
        theta: 0,
        asset: { url: "http://img.test/query.jpg" },
        encounter: { id: "enc-query", locationId: "loc-1" },
        individual: { id: "ind-query", displayName: "Luna" },
      },
      prospects: {
        annot: [{ annotId: "a1", score: 0.9 }],
        indiv: [{ individualId: "i1", score: 0.85 }],
      },
    },
    children: [],
  },
});

const renderComponent = (url = "/match-results?taskId=task-1") =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <MemoryRouter initialEntries={[url]}>
        <MatchResults />
      </MemoryRouter>
    </IntlProvider>,
  );

// ---------------------------------------------------------------------------

describe("MatchResults component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("shows loader while fetching", async () => {
    let resolveRequest;
    axios.get.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveRequest = () => resolve({ data: makeApiResponse() });
        }),
    );
    renderComponent();
    expect(screen.getByTestId("full-screen-loader")).toBeInTheDocument();
    await act(async () => {
      resolveRequest();
    });
  });

  test("shows 'no match results' message when no taskId in URL", async () => {
    renderComponent("/match-results");
    expect(
      await screen.findByText(/no match results available for this job/i),
    ).toBeInTheDocument();
  });

  test("renders match prospect table after successful fetch", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    expect(
      await screen.findByTestId("prospect-table-task-1"),
    ).toBeInTheDocument();
  });

  test("renders bottom bar when results are available", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    expect(await screen.findByTestId("bottom-bar")).toBeInTheDocument();
  });

  test("renders INDIVIDUAL_SCORE and IMAGE_SCORE view mode buttons", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByText("INDIVIDUAL_SCORE")).toBeInTheDocument();
    expect(screen.getByText("IMAGE_SCORE")).toBeInTheDocument();
  });

  test("clicking IMAGE_SCORE button does not crash", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    await act(async () => {
      fireEvent.click(screen.getByText("IMAGE_SCORE"));
    });
    expect(screen.getByText("IMAGE_SCORE")).toBeInTheDocument();
  });

  test("InfoIcon click opens InstructionsModal", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.queryByTestId("instructions-modal")).not.toBeInTheDocument();
    const infoWrapper = screen.getByTitle("Match Page Instructions");
    fireEvent.click(within(infoWrapper).getByRole("button"));
    expect(screen.getByTestId("instructions-modal")).toBeInTheDocument();
  });

  test("Closing InstructionsModal hides it", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const infoWrapper = screen.getByTitle("Match Page Instructions");
    fireEvent.click(within(infoWrapper).getByRole("button"));
    fireEvent.click(screen.getByTestId("close-instructions"));
    expect(screen.queryByTestId("instructions-modal")).not.toBeInTheDocument();
  });

  test("FilterIcon click opens MatchCriteriaDrawer", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(
      screen.queryByTestId("match-criteria-drawer"),
    ).not.toBeInTheDocument();
    fireEvent.click(screen.getByTitle("Match Criteria"));
    expect(screen.getByTestId("match-criteria-drawer")).toBeInTheDocument();
  });

  test("Closing MatchCriteriaDrawer hides it", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    fireEvent.click(screen.getByTitle("Match Criteria"));
    fireEvent.click(screen.getByTestId("close-drawer"));
    expect(
      screen.queryByTestId("match-criteria-drawer"),
    ).not.toBeInTheDocument();
  });

  test("NUMBER_OF_RESULTS label is rendered", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByText("NUMBER_OF_RESULTS")).toBeInTheDocument();
  });

  test("numResults input accepts numeric value", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.change(input, { target: { value: "20" } });
    expect(input.value).toBe("20");
  });

  test("non-numeric input is rejected in numResults field", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.change(input, { target: { value: "abc" } });
    expect(input.value).toBe("12");
  });

  test("pressing Enter on numResults input triggers fetch", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));
  });

  test("focus on numResults input shows confirm checkmark button", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    fireEvent.focus(screen.getByDisplayValue("12"));
    expect(screen.getByTitle("Apply changes")).toBeInTheDocument();
  });

  test("blur on numResults input hides confirm checkmark button", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    const input = screen.getByDisplayValue("12");
    fireEvent.focus(input);
    fireEvent.blur(input);
    expect(screen.queryByTitle("Apply changes")).not.toBeInTheDocument();
  });

  test("renders PROJECT label and MultiSelectWithCheckbox", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByTestId("project-multiselect")).toBeInTheDocument();
  });

  test("project options are derived from projectsForUser site settings", async () => {
    axios.get.mockResolvedValueOnce({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    expect(screen.getByText("Project Alpha")).toBeInTheDocument();
    expect(screen.getByText("Project Beta")).toBeInTheDocument();
  });

  test("selecting a project triggers re-fetch", async () => {
    axios.get.mockResolvedValue({ data: makeApiResponse() });
    renderComponent();
    await screen.findByTestId("prospect-table-task-1");
    await act(async () => {
      fireEvent.change(screen.getByTestId("project-multiselect"), {
        target: { value: "proj-1" },
      });
    });
    await waitFor(() => expect(axios.get).toHaveBeenCalledTimes(2));
  });

  test("shows 'no match results' message when API returns empty prospects", async () => {
    axios.get.mockResolvedValueOnce({
      data: {
        matchResultsRoot: {
          id: "task-1",
          method: { name: "hs" },
          matchResults: {
            numberCandidates: 0,
            queryAnnotation: {},
            prospects: { annot: [], indiv: [] },
          },
          children: [],
        },
      },
    });
    renderComponent();
    expect(
      await screen.findByText(/no match results available for this job/i),
    ).toBeInTheDocument();
  });

  test("does not crash when API call fails", async () => {
    axios.get.mockRejectedValueOnce(new Error("network error"));
    renderComponent();
    expect(
      await screen.findByText(/no match results available for this job/i),
    ).toBeInTheDocument();
  });
});
