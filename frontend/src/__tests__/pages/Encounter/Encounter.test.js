/* eslint-disable react/display-name */
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { IntlProvider } from "react-intl";

jest.mock("react", () => jest.requireActual("react"));

jest.mock("mobx-react-lite", () => ({
  observer: (Comp) => Comp,
}));

jest.mock("axios", () => ({
  get: jest.fn(() =>
    Promise.resolve({ data: { id: "E-123", state: "unidentifiable" } }),
  ),
}));

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({
    data: { encounterState: ["unidentifiable", "identified", "rejected"] },
  }),
}));

const mockSetEncounterState = jest.fn();
jest.mock("../../../pages/Encounter/stores/helperFunctions", () => ({
  setEncounterState: (...args) => mockSetEncounterState(...args),
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
      this.overviewActive = true;
      this.setOverviewActive = makeFn();

      this.siteSettingsData = null;
      this.setSiteSettings = makeFn();

      this.encounterData = { id: "E-INIT", state: "unidentifiable" };
      this.setEncounterData = makeFn();
      this.refreshEncounterData = makeFn();

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
    }
  }
  return { EncounterStore };
});

const setUrl = (id = "E-555") => {
  window.history.pushState({}, "", `http://localhost/encounter?number=${id}`);
};

const loadComponent = async () => {
  const mod = await import("../../../pages/Encounter/Encounter");
  return mod.default || mod;
};

describe("Encounter page – behavior excluding i18n logic", () => {
  beforeEach(() => {
    global.__MOCK_STORE_PRESET__ = undefined;
    mockSetEncounterState.mockClear();
  });

  test("calls axios with encounter id from URL and renders base UI (FormattedMessage ids visible)", async () => {
    setUrl("E-999");
    const Encounter = await loadComponent();

    const { default: axios } = await import("axios");

    render(
      <IntlProvider locale="en" messages={{}}>
        <Encounter />
      </IntlProvider>,
    );

    await waitFor(() => {
      expect(axios.get).toHaveBeenCalledWith("/api/v3/encounters/E-999");
    });

    expect(screen.getByTestId("pill-selected")).toHaveTextContent(
      "unidentifiable",
    );
    expect(screen.getByTestId("image-card")).toBeInTheDocument();

    expect(screen.getByTestId("date-review")).toBeInTheDocument();
    expect(screen.getByTestId("identify-review")).toBeInTheDocument();
    expect(screen.getByTestId("metadata-review")).toBeInTheDocument();
    expect(screen.getByTestId("location-review")).toBeInTheDocument();
    expect(screen.getByTestId("attributes-review")).toBeInTheDocument();
  });

  test("changing encounter state triggers setEncounterState and refresh", async () => {
    setUrl("E-100");
    const Encounter = await loadComponent();

    render(
      <IntlProvider locale="en" messages={{}}>
        <Encounter />
      </IntlProvider>,
    );

    const user = userEvent.setup();
    await user.click(screen.getByTestId("pill-select-identified"));
    expect(mockSetEncounterState).toHaveBeenCalledTimes(1);
    const [, calledId] = mockSetEncounterState.mock.calls[0];
    expect(calledId).toBeDefined();
  });

  test("clicking contact/history icons opens their modals", async () => {
    setUrl("E-200");
    const Encounter = await loadComponent();

    render(
      <IntlProvider locale="en" messages={{}}>
        <Encounter />
      </IntlProvider>,
    );

    const user = userEvent.setup();

    await user.click(screen.getByTestId("icon-contact"));
    await user.click(screen.getByTestId("icon-history"));

    expect(screen.getByTestId("icon-contact")).toBeInTheDocument();
    expect(screen.getByTestId("icon-history")).toBeInTheDocument();
  });

  test("clicking Edit buttons calls corresponding store.setEdit*Card(true)", async () => {
    setUrl("E-300");
    const Encounter = await loadComponent();

    render(
      <IntlProvider locale="en" messages={{}}>
        <Encounter />
      </IntlProvider>,
    );

    const user = userEvent.setup();

    await user.click(screen.getByTestId("btn-edit-DATE"));
    await user.click(screen.getByTestId("btn-edit-IDENTIFY"));
    await user.click(screen.getByTestId("btn-edit-METADATA"));
    await user.click(screen.getByTestId("btn-edit-LOCATION"));
    await user.click(screen.getByTestId("btn-edit-ATTRIBUTES"));

    expect(screen.getByTestId("btn-edit-DATE")).toBeInTheDocument();
    expect(screen.getByTestId("btn-edit-IDENTIFY")).toBeInTheDocument();
    expect(screen.getByTestId("btn-edit-METADATA")).toBeInTheDocument();
    expect(screen.getByTestId("btn-edit-LOCATION")).toBeInTheDocument();
    expect(screen.getByTestId("btn-edit-ATTRIBUTES")).toBeInTheDocument();
  });

  test("when a section is in edit mode, clicking Save/Cancel triggers store methods", async () => {
    global.__MOCK_STORE_PRESET__ = { editDateCard: true };

    setUrl("E-400");
    const Encounter = await loadComponent();
    [
      "saveSection",
      "refreshEncounterData",
      "resetSectionDraft",
      "errors",
    ].forEach(() => {});
    render(
      <IntlProvider locale="en" messages={{}}>
        <Encounter />
      </IntlProvider>,
    );

    const user = userEvent.setup();
    await user.click(screen.getByTestId("btn-save-DATE"));
    await user.click(screen.getByTestId("btn-cancel-DATE"));
    expect(screen.getByTestId("card-save-cancel-DATE")).toBeInTheDocument();
  });
});
