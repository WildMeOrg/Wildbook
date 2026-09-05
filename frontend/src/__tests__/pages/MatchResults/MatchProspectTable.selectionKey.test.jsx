import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchProspectTable from "../../../pages/MatchResultsPage/components/MatchProspectTable";

jest.mock("../../../components/AnnotationOverlay", () => {
  const React = require("react");
  const Overlay = React.forwardRef(function Overlay() {
    return React.createElement("div", { "data-testid": "annotation-overlay" });
  });
  Overlay.displayName = "InteractiveAnnotationOverlay";
  return Overlay;
});

jest.mock("../../../pages/MatchResultsPage/components/InspectorModal", () => {
  function InspectorModal() {
    return null;
  }
  InspectorModal.displayName = "InspectorModal";
  return InspectorModal;
});

jest.mock("../../../api/client", () => ({
  client: { get: jest.fn(), post: jest.fn() },
}));

const themeColor = {
  primaryColors: {
    primary50: "#E5F6FF",
    primary500: "#00ACCE",
    primary700: "#007599",
  },
  wildMeColors: { teal100: "#CCF2F5", teal800: "#005A66" },
};

// The SAME prospect (annotation ann-X at rank 1) appears in two sections —
// the shape an image-wide umbrella task produces for two annotations that
// both match the same candidate.
const candidate = () => ({
  annotation: { id: "ann-X", encounter: { id: "enc-X" }, individual: null },
  displayIndex: 1,
  score: 0.9,
});

const renderTable = (taskId, onToggleSelected, selectedMatch = []) =>
  render(
    <IntlProvider locale="en" messages={{}} onError={() => {}}>
      <MatchProspectTable
        sectionId={`image-${taskId}`}
        taskId={taskId}
        columns={[[candidate()]]}
        selectedMatch={selectedMatch}
        onToggleSelected={onToggleSelected}
        themeColor={themeColor}
        numCandidates={5}
        date="2024-06-01"
        taskStatusOverall="completed"
      />
    </IntlProvider>,
  );

describe("MatchProspectTable — task-qualified selection keys (issue #1744)", () => {
  test("the same prospect in two sections yields distinct selection keys", () => {
    const onToggle = jest.fn();
    renderTable("task-A", onToggle);
    renderTable("task-B", onToggle);

    const boxes = screen.getAllByTestId(/^match-prospect-select-/);
    expect(boxes).toHaveLength(2);
    fireEvent.click(boxes[0]);
    fireEvent.click(boxes[1]);

    expect(onToggle).toHaveBeenCalledTimes(2);
    const [callA, callB] = onToggle.mock.calls;
    expect(callA[0]).toBe(true);
    expect(callA[1]).not.toBe(callB[1]);
    expect(callA[1]).toContain("task-A");
    expect(callB[1]).toContain("task-B");
    expect(callA[2]).toBe("enc-X"); // candidate encounter still forwarded
  });

  test("a row reads as selected only under its own task-qualified key", () => {
    const onToggle = jest.fn();
    const { unmount } = renderTable("task-A", onToggle);
    fireEvent.click(screen.getByTestId(/^match-prospect-select-/));
    const keyA = onToggle.mock.calls[0][1];
    unmount();

    renderTable("task-B", jest.fn(), [{ key: keyA, encounterId: "enc-X" }]);
    expect(screen.getByTestId(/^match-prospect-select-/)).not.toBeChecked();

    renderTable("task-A", jest.fn(), [{ key: keyA, encounterId: "enc-X" }]);
    const checked = screen
      .getAllByTestId(/^match-prospect-select-/)
      .filter((el) => el.checked);
    expect(checked).toHaveLength(1);
  });
});
