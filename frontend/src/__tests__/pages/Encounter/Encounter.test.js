/* eslint-disable react/display-name */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";
import axios from "axios";

jest.mock("react", () => jest.requireActual("react"));

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("axios", () => {
  const api = { get: jest.fn() };
  return { __esModule: true, default: api, ...api };
});

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({
    data: { encounterState: ["unidentifiable", "identified", "rejected"] },
    loading: false,
  }),
}));

jest.mock("../../../components/icons/DateIcon", () => () => (
  <span data-testid="icon-date" />
));
jest.mock("../../../components/IdentifyIcon", () => () => (
  <span data-testid="icon-identify" />
));
jest.mock("../../../components/icons/MetaDataIcon", () => () => (
  <span data-testid="icon-metadata" />
));
jest.mock("../../../components/icons/LocationIcon", () => () => (
  <span data-testid="icon-location" />
));
jest.mock("../../../components/icons/AttributesIcon", () => () => (
  <span data-testid="icon-attributes" />
));
jest.mock("../../../components/icons/ContactIcon", () => () => (
  <span data-testid="icon-contact" />
));
jest.mock("../../../components/icons/HistoryIcon", () => () => (
  <span data-testid="icon-history" />
));

jest.mock("../../../components/Pill", () => (props) => (
  <button data-testid={`pill-${props.text}`} onClick={props.onClick}>
    {props.text}
  </button>
));

jest.mock("../../../components/PillWithDropdown", () => (props) => (
  <div data-testid="pill-with-dropdown">
    <div data-testid="pill-selected">{props.selectedOption}</div>
    <button
      data-testid="pill-select-identified"
      onClick={() => props.onSelect && props.onSelect("identified")}
    >
      select-identified
    </button>
  </div>
));

jest.mock("../../../components/CardWithEditButton", () => (props) => (
  <div data-testid={`card-edit-${props.title}`}>
    <div data-testid={`card-edit-title-${props.title}`}>{props.title}</div>
    {props.content}
    <button data-testid={`btn-edit-${props.title}`} onClick={props.onClick}>
      edit-{props.title}
    </button>
  </div>
));

jest.mock("../../../components/CardWithSaveAndCancelButtons", () => (props) => (
  <div data-testid={`card-save-cancel-${props.title}`}>
    <div data-testid={`card-save-cancel-title-${props.title}`}>
      {props.title}
    </div>
    {props.content}
    <button
      data-testid={`btn-save-${props.title}`}
      onClick={props.onSave}
      disabled={props.disabled}
    >
      save-{props.title}
    </button>
    <button data-testid={`btn-cancel-${props.title}`} onClick={props.onCancel}>
      cancel-{props.title}
    </button>
  </div>
));

jest.mock(
  "../../../pages/Encounter/ContactInfoModal",
  () => (p) => (p.isOpen ? <div data-testid="contact-modal-open" /> : null),
);
jest.mock(
  "../../../pages/Encounter/EncounterHistoryModal",
  () => (p) => (p.isOpen ? <div data-testid="history-modal-open" /> : null),
);
jest.mock(
  "../../../pages/Encounter/MatchCriteria",
  () => (p) => (p.isOpen ? <div data-testid="match-modal-open" /> : null),
);

jest.mock("../../../pages/Encounter/ImageCard", () => () => (
  <div data-testid="image-card" />
));
jest.mock("../../../pages/Encounter/MoreDetails", () => ({
  MoreDetails: () => <div data-testid="more-details" />,
}));
jest.mock("../../../pages/Encounter/DeleteEncounterCard", () => () => (
  <div data-testid="delete-encounter-card" />
));
jest.mock("../../../pages/Encounter/CollabModal", () => () => (
  <div data-testid="collab-modal" />
));

jest.mock("../../../pages/Encounter/DateSectionReview", () => ({
  DateSectionReview: () => <div data-testid="date-review" />,
}));
jest.mock("../../../pages/Encounter/IdentifySectionReview", () => ({
  IdentifySectionReview: () => <div data-testid="identify-review" />,
}));
jest.mock("../../../pages/Encounter/MetadataSectionReview", () => ({
  MetadataSectionReview: () => <div data-testid="metadata-review" />,
}));
jest.mock("../../../pages/Encounter/LocationSectionReview", () => ({
  LocationSectionReview: () => <div data-testid="location-review" />,
}));
jest.mock("../../../pages/Encounter/AttributesSectionReview", () => ({
  AttributesSectionReview: () => <div data-testid="attributes-review" />,
}));

jest.mock("../../../pages/Encounter/DateSectionEdit", () => ({
  DateSectionEdit: () => <div data-testid="date-edit" />,
}));
jest.mock("../../../pages/Encounter/IdentifySectionEdit", () => ({
  IdentifySectionEdit: () => <div data-testid="identify-edit" />,
}));
jest.mock("../../../pages/Encounter/MetadataSectionEdit", () => ({
  MetadataSectionEdit: () => <div data-testid="metadata-edit" />,
}));
jest.mock("../../../pages/Encounter/LocationSectionEdit", () => ({
  LocationSectionEdit: () => <div data-testid="location-edit" />,
}));
jest.mock("../../../pages/Encounter/AttributesSectionEdit", () => ({
  AttributesSectionEdit: () => <div data-testid="attributes-edit" />,
}));

jest.mock("../../../pages/Encounter/stores", () => {
  const makeFn = () => jest.fn();

  class EncounterStore {
    constructor() {
      const preset = global.__MOCK_STORE_PRESET__ || {};

      this.overviewActive = preset.overviewActive ?? true;
      this.setOverviewActive = makeFn();

      this.siteSettingsData = preset.siteSettingsData ?? null;
      this.setSiteSettings = jest.fn((data) => {
        this.siteSettingsData = data;
      });

      this.siteSettingsLoading = false;
      this.setSiteSettingsLoading = makeFn();

      this.encounterData = preset.encounterData ?? {
        id: "E-INIT",
        state: "unidentifiable",
      };
      this.setEncounterData = jest.fn((data) => {
        this.encounterData = data;
      });
      this.refreshEncounterData = makeFn();

      this.access = preset.access ?? "write";
      this.setAccess = jest.fn((value) => {
        this.access = value;
      });

      this.saveSection = makeFn();
      this.resetSectionDraft = makeFn();

      this.errors = {
        getFieldError: makeFn(),
        setFieldError: makeFn(),
        clearSectionErrors: makeFn(),
      };

      this.editDateCard = !!preset.editDateCard;
      this.setEditDateCard = makeFn();

      this.editIdentifyCard = !!preset.editIdentifyCard;
      this.setEditIdentifyCard = makeFn();

      this.changeEncounterState = makeFn();

      this.editMetadataCard = !!preset.editMetadataCard;
      this.setEditMetadataCard = makeFn();

      this.editLocationCard = !!preset.editLocationCard;
      this.setEditLocationCard = makeFn();

      this.editAttributesCard = !!preset.editAttributesCard;
      this.setEditAttributesCard = makeFn();

      this.modals = {
        openContactInfoModal: false,
        setOpenContactInfoModal: makeFn(),
        openEncounterHistoryModal: false,
        setOpenEncounterHistoryModal: makeFn(),
        openMatchCriteriaModal: false,
        setOpenMatchCriteriaModal: makeFn(),
      };

      global.__LAST_ENCOUNTER_STORE__ = this;
    }
  }

  return { EncounterStore };
});

const setUrl = (id = "E-555") => {
  window.history.pushState({}, "", `/encounter?number=${id}`);
};

const renderEncounter = () => {
  let Encounter;
  jest.isolateModules(() => {
    Encounter = require("../../../pages/Encounter/Encounter").default;
  });

  render(
    <IntlProvider locale="en" messages={{}}>
      <Encounter />
    </IntlProvider>,
  );
};

describe("Encounter page – stable behavior tests", () => {
  beforeEach(() => {
    jest.useRealTimers();
    global.__MOCK_STORE_PRESET__ = undefined;
    global.__LAST_ENCOUNTER_STORE__ = undefined;
    jest.clearAllMocks();
  });

  test("loads encounter by id from URL and renders main sections", () => {
    setUrl("E-999");

    axios.get.mockResolvedValueOnce({
      data: { id: "E-999", state: "unidentifiable", access: "write" },
    });

    renderEncounter();

    expect(screen.getByTestId("pill-selected")).toBeInTheDocument();

    expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-999");
  });

  test("changing encounter state triggers store.changeEncounterState", async () => {
    setUrl("E-100");

    axios.get.mockResolvedValueOnce({
      data: { id: "E-100", state: "unidentifiable", access: "write" },
    });

    renderEncounter();

    const user = userEvent.setup();
    await user.click(await screen.findByTestId("pill-select-identified"));

    expect(global.__LAST_ENCOUNTER_STORE__.changeEncounterState).toHaveBeenCalledWith(
      "identified",
    );
  });

  test("clicking contact/history icons opens modals via store setters", async () => {
    setUrl("E-200");

    axios.get.mockResolvedValueOnce({
      data: { id: "E-200", state: "unidentifiable", access: "write" },
    });

    renderEncounter();

    const user = userEvent.setup();
    await user.click(await screen.findByTestId("icon-contact"));
    await user.click(screen.getByTestId("icon-history"));

    expect(
      global.__LAST_ENCOUNTER_STORE__.modals.setOpenContactInfoModal,
    ).toHaveBeenCalledWith(true);
    expect(
      global.__LAST_ENCOUNTER_STORE__.modals.setOpenEncounterHistoryModal,
    ).toHaveBeenCalledWith(true);
  });

  test("date section: in edit mode, Save and Cancel call store methods", async () => {
    global.__MOCK_STORE_PRESET__ = { editDateCard: true, access: "write" };
    setUrl("E-400");

    axios.get.mockResolvedValueOnce({
      data: { id: "E-400", state: "unidentifiable", access: "write" },
    });

    renderEncounter();

    const user = userEvent.setup();

    await user.click(await screen.findByTestId("btn-save-DATE"));

    expect(global.__LAST_ENCOUNTER_STORE__.saveSection).toHaveBeenCalledWith(
      "date",
      "E-400",
    );
    expect(global.__LAST_ENCOUNTER_STORE__.setEditDateCard).toHaveBeenCalledWith(false);
    expect(global.__LAST_ENCOUNTER_STORE__.refreshEncounterData).toHaveBeenCalled();

    await user.click(screen.getByTestId("btn-cancel-DATE"));

    expect(global.__LAST_ENCOUNTER_STORE__.resetSectionDraft).toHaveBeenCalledWith("date");
    expect(global.__LAST_ENCOUNTER_STORE__.setEditDateCard).toHaveBeenCalledWith(false);
    expect(global.__LAST_ENCOUNTER_STORE__.errors.setFieldError).toHaveBeenCalledWith(
      "date",
      "date",
      null,
    );
    expect(global.__LAST_ENCOUNTER_STORE__.errors.clearSectionErrors).toHaveBeenCalledWith(
      "date",
    );
  });
});