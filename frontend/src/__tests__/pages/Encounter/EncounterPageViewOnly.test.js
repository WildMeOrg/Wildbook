/* eslint-disable react/display-name */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("axios", () => ({
  get: jest.fn(() => Promise.resolve({ data: {} })),
}));

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("../../../components/icons/DateIcon", () => () => (
  <span data-testid="icon-date" />
));
jest.mock("../../../components/icons/MailIcon", () => () => (
  <span data-testid="icon-mail" />
));
jest.mock("../../../components/IdentifyIcon", () => () => (
  <span data-testid="icon-identify" />
));
jest.mock("../../../components/icons/AttributesIcon", () => () => (
  <span data-testid="icon-attributes" />
));
jest.mock("../../../components/CardWithoutEditButton", () => (props) => (
  <div data-testid={`card-${props.title}`}>
    <div data-testid={`card-title-${props.title}`}>{props.title}</div>
    <div data-testid={`card-content-${props.title}`}>{props.content}</div>
  </div>
));
jest.mock("../../../components/LoadingScreen", () => () => (
  <div data-testid="loading-screen">Loading...</div>
));

import EncounterPageViewOnly from "../../../pages/Encounter/EncounterPageViewOnly";
import axios from "axios";

const renderPage = () =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <EncounterPageViewOnly />
    </IntlProvider>,
  );

describe("EncounterPageViewOnly", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("loads encounter by id, renders headings/fields, and switches images on thumbnail click", async () => {
    window.history.pushState({}, "", "http://localhost/encounter?number=E-777");

    const payload = {
      isPublic: true,
      individualDisplayName: "Flipper",
      date: "2025-01-02",
      verbatimEventDate: "Jan 2, 2025",
      identificationRemarks: "Auto-matched",
      otherCatalogNumbers: "ALT-123",
      taxonomy: "mammal",
      livingStatus: "alive",
      sex: "female",
      distinguishingScar: "right fin nick",
      behavior: "foraging",
      groupRole: "leader",
      patterningCode: "B",
      lifeStage: "adult",
      occurrenceRemarks: "near reef",
      mediaAssets: [
        { url: "http://img/1.jpg" },
        { url: "http://img/2.jpg" },
        { url: "http://img/3.jpg" },
      ],
    };
    axios.get.mockResolvedValueOnce({ data: payload });

    renderPage();

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-777");
    });

    expect(screen.getByText(/ENCOUNTER_ID/i)).toBeInTheDocument();
    expect(screen.getByText(/of Flipper/)).toBeInTheDocument();
    expect(screen.getByText(/E-777/)).toBeInTheDocument();

    const mainImg = await screen.findByAltText("Encounter Image");
    expect(mainImg).toHaveAttribute("src", "http://img/1.jpg");

    const thumbs = [
      screen.getByAltText("img-1"),
      screen.getByAltText("img-2"),
      screen.getByAltText("img-3"),
    ];
    expect(thumbs).toHaveLength(3);

    const user = userEvent.setup();

    await user.click(thumbs[1]);
    expect(screen.getByAltText("Encounter Image")).toHaveAttribute(
      "src",
      "http://img/2.jpg",
    );

    await user.click(thumbs[2]);
    expect(screen.getByAltText("Encounter Image")).toHaveAttribute(
      "src",
      "http://img/3.jpg",
    );
  });

  test("renders NO_IMAGE_AVAILABLE when mediaAssets is empty", async () => {
    window.history.pushState({}, "", "http://localhost/encounter?number=E-999");

    axios.get.mockResolvedValueOnce({
      data: {
        isPublic: true,
        individualDisplayName: "",
        mediaAssets: [],
      },
    });

    renderPage();

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-999");
    });

    expect(screen.queryByAltText("Encounter Image")).toBeNull();
    expect(screen.queryByAltText(/img-\d+/)).toBeNull();
    expect(screen.getByText("NO_IMAGE_AVAILABLE")).toBeInTheDocument();
  });
});
