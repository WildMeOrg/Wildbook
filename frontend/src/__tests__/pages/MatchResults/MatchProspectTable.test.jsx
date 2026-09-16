import React from "react";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchProspectTable from "../../../pages/MatchResultsPage/components/MatchProspectTable";

jest.mock("../../../components/AnnotationOverlay", () => {
  const React = require("react");
  const InteractiveAnnotationOverlay = React.forwardRef(
    function InteractiveAnnotationOverlay(props, ref) {
      return React.createElement("div", {
        "data-testid": "annotation-overlay-mock",
      });
    },
  );
  return InteractiveAnnotationOverlay;
});

jest.mock("../../../pages/MatchResultsPage/components/InspectorModal", () => {
  const React = require("react");
  function InspectorModal() {
    return null;
  }
  InspectorModal.displayName = "InspectorModal";
  return InspectorModal;
});

jest.mock("../../../api/client", () => ({
  client: { post: jest.fn() },
}));

// ---------------------------------------------------------------------------

const themeColor = {
  primaryColors: {
    primary50: "#E5F6FF",
    primary500: "#00ACCE",
    primary700: "#007A93",
  },
  wildMeColors: {
    teal100: "#CCF0F5",
    teal800: "#00505F",
  },
};

const messages = {
  THIS_ENCOUNTER: "This Encounter",
  POSSIBLE_MATCH: "Possible Match",
  MATCHED_BASED_ON: "Matched based on",
  NO_MATCH_RESULT: "No match results available.",
  NO_MATCH_PROSPECTS: "No match prospects",
};

const renderTable = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={messages} onError={() => {}}>
      <MatchProspectTable
        sectionId="individual-t1"
        columns={[]}
        themeColor={themeColor}
        {...props}
      />
    </IntlProvider>,
  );

describe("MatchProspectTable — This Encounter label link", () => {
  test("renders a link to the query encounter when thisEncounterId is provided", () => {
    renderTable({ thisEncounterId: "enc-123" });

    const link = screen.getByTestId(
      "match-prospect-left-label-link-individual-t1",
    );
    expect(link.tagName).toBe("A");
    expect(link).toHaveAttribute(
      "href",
      "/react/encounter?number=enc-123",
    );
    expect(link).toHaveAttribute("target", "_blank");
    expect(link).toHaveAttribute("rel", "noopener noreferrer");
    // setupTests.js mocks FormattedMessage to render the raw message id
    expect(link).toHaveTextContent("THIS_ENCOUNTER");
  });

  test("URL-encodes the encounter id in the link href", () => {
    renderTable({ thisEncounterId: "enc/we ird" });

    const link = screen.getByTestId(
      "match-prospect-left-label-link-individual-t1",
    );
    expect(link).toHaveAttribute(
      "href",
      `/react/encounter?number=${encodeURIComponent("enc/we ird")}`,
    );
  });

  test("renders plain label text without a link when thisEncounterId is absent", () => {
    renderTable();

    const label = screen.getByTestId("match-prospect-left-label-individual-t1");
    expect(label).toHaveTextContent("THIS_ENCOUNTER");
    expect(within(label).queryByRole("link")).not.toBeInTheDocument();
  });

  test("still links while the task is running with no prospects yet", () => {
    renderTable({
      thisEncounterId: "enc-123",
      taskStatusOverall: "running",
    });

    expect(
      screen.getByTestId("match-prospect-left-label-link-individual-t1"),
    ).toHaveAttribute("href", "/react/encounter?number=enc-123");
  });

  const makeCandidate = () => ({
    annotation: {
      id: "ann-1",
      asset: { url: "http://img.test/cand.jpg", width: 100, height: 80 },
    },
    score: 0.9,
    displayIndex: 1,
  });

  test("fullscreen modal label links to the query encounter", () => {
    renderTable({
      thisEncounterId: "enc-123",
      thisEncounterImageUrl: "http://img.test/query.jpg",
      columns: [[makeCandidate()]],
    });

    fireEvent.click(
      screen.getByTestId("match-prospect-fullscreen-open-individual-t1"),
    );

    const link = screen.getByTestId(
      "match-prospect-fullscreen-left-label-link-individual-t1",
    );
    expect(link).toHaveAttribute("href", "/react/encounter?number=enc-123");
    expect(link).toHaveAttribute("target", "_blank");
  });

  test("fullscreen modal label has no link when thisEncounterId is absent", () => {
    renderTable({
      thisEncounterImageUrl: "http://img.test/query.jpg",
      columns: [[makeCandidate()]],
    });

    fireEvent.click(
      screen.getByTestId("match-prospect-fullscreen-open-individual-t1"),
    );

    const label = screen.getByTestId(
      "match-prospect-fullscreen-left-label-individual-t1",
    );
    expect(label).toHaveTextContent("THIS_ENCOUNTER");
    expect(within(label).queryByRole("link")).not.toBeInTheDocument();
  });
});
