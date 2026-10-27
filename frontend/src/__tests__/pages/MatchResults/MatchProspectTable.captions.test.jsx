import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import MatchProspectTable from "../../../pages/MatchResultsPage/components/MatchProspectTable";

jest.mock("../../../components/AnnotationOverlay", () => {
  const React = require("react");
  const InteractiveAnnotationOverlay = React.forwardRef(
    function InteractiveAnnotationOverlay() {
      return React.createElement("div", {
        "data-testid": "annotation-overlay-mock",
      });
    },
  );
  return InteractiveAnnotationOverlay;
});

jest.mock("../../../pages/MatchResultsPage/components/InspectorModal", () => {
  function InspectorModal() {
    return null;
  }
  InspectorModal.displayName = "InspectorModal";
  return InspectorModal;
});

jest.mock("../../../api/client", () => ({
  client: { post: jest.fn() },
}));

// Issue #1733: date, filename and (when sent) location ID on match images.

const themeColor = {
  primaryColors: { primary50: "#E5F6FF", primary500: "#00ACCE" },
  wildMeColors: { teal100: "#CCF0F5", teal800: "#00505F" },
};

const candidate = (n, { locationId, date, filename } = {}) => ({
  annotation: {
    id: `ann-${n}`,
    asset: {
      url: `http://img.test/cand-${n}.jpg`,
      width: 100,
      height: 80,
      ...(filename ? { filename } : {}),
    },
    encounter: {
      id: `enc-${n}`,
      ...(date ? { date } : {}),
      ...(locationId ? { locationId } : {}),
    },
  },
  // a lazily fetched PairX asset must never feed the caption
  asset: { url: `http://img.test/pairx-${n}.jpg`, filename: "pairx.png" },
  score: 0.9 - n / 10,
  displayIndex: n,
});

const renderTable = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={{}} onError={() => {}}>
      <MatchProspectTable
        sectionId="s1"
        themeColor={themeColor}
        thisEncounterId="enc-q"
        thisEncounterImageUrl="http://img.test/query.jpg"
        thisEncounterImageAsset={{
          url: "http://img.test/query.jpg",
          filename: "GoPro_0193.JPG",
        }}
        thisEncounterDate="2026-10-18 08:05"
        thisEncounterLocationId="Ningaloo Reef"
        columns={[
          [
            candidate(1, {
              date: "2019-04-22 10:31",
              filename: "DSC_4471.JPG",
              locationId: "Coral Bay",
            }),
            candidate(2, { date: "2021", filename: "IMG_9.jpg" }),
          ],
        ]}
        {...props}
      />
    </IntlProvider>,
  );

const text = (id) => screen.queryByTestId(id)?.textContent ?? null;

describe("MatchProspectTable image captions (issue #1733)", () => {
  test("query image shows its date, filename and location", () => {
    renderTable();
    expect(text("match-prospect-left-caption-s1-date")).toBe(
      "2026-10-18 08:05",
    );
    expect(text("match-prospect-left-caption-s1-filename")).toBe(
      "GoPro_0193.JPG",
    );
    expect(text("match-prospect-left-caption-s1-location")).toBe(
      "Ningaloo Reef",
    );
  });

  test("candidate caption follows the previewed row, from its own annotation", () => {
    renderTable();
    expect(text("match-prospect-right-caption-s1-date")).toBe(
      "2019-04-22 10:31",
    );
    expect(text("match-prospect-right-caption-s1-filename")).toBe(
      "DSC_4471.JPG",
    );
    expect(text("match-prospect-right-caption-s1-location")).toBe("Coral Bay");

    // preview the second candidate: the server sent no locationId for it
    fireEvent.click(screen.getByTestId("match-prospect-row-s1-ann-2-2"));
    expect(text("match-prospect-right-caption-s1-date")).toBe("2021");
    expect(text("match-prospect-right-caption-s1-filename")).toBe("IMG_9.jpg");
    expect(
      screen.queryByTestId("match-prospect-right-caption-s1-location"),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("pairx.png")).not.toBeInTheDocument();
  });

  test("fullscreen panels carry the same captions", () => {
    renderTable();
    fireEvent.click(screen.getByTestId("match-prospect-row-s1-ann-2-2"));
    fireEvent.click(screen.getByTestId("match-prospect-fullscreen-open-s1"));
    expect(text("match-prospect-fullscreen-left-caption-s1-filename")).toBe(
      "GoPro_0193.JPG",
    );
    expect(text("match-prospect-fullscreen-left-caption-s1-location")).toBe(
      "Ningaloo Reef",
    );
    expect(text("match-prospect-fullscreen-right-caption-s1-filename")).toBe(
      "IMG_9.jpg",
    );
    expect(
      screen.queryByTestId(
        "match-prospect-fullscreen-right-caption-s1-location",
      ),
    ).not.toBeInTheDocument();
  });

  test("no caption when there is nothing to show", () => {
    renderTable({
      thisEncounterImageAsset: { url: "http://img.test/query.jpg" },
      thisEncounterDate: null,
      thisEncounterLocationId: null,
      columns: [[candidate(1)]],
    });
    expect(
      screen.queryByTestId("match-prospect-left-caption-s1"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("match-prospect-right-caption-s1"),
    ).not.toBeInTheDocument();
  });
});
