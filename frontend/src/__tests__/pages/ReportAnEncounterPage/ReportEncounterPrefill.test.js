import React from "react";
import { fireEvent, screen } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import ReportEncounter from "../../../pages/ReportsAndManagamentPages/ReportEncounter";
import { useSiteSettings } from "../../../SiteSettingsContext";

jest.mock("../../../SiteSettingsContext", () => ({
  __esModule: true,
  useSiteSettings: jest.fn(),
}));

jest.mock("../../../pages/ReportsAndManagamentPages/ImageSection", () => ({
  ImageSection: () => null,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/DateTimeSection", () => ({
  DateTimeSection: () => null,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/PlaceSection", () => ({
  PlaceSection: () => null,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/SpeciesSection", () => ({
  ReportEncounterSpeciesSection: () => null,
}));
jest.mock("../../../components/AdditionalCommentsSection", () => ({
  AdditionalCommentsSection: () => null,
}));

const currentUser = {
  displayName: "Alex Doe",
  email: "alex@example.com",
};

const saveContactValues = () => {
  localStorage.setItem("followUpSection.submitter.name", "Saved Submitter");
  localStorage.setItem(
    "followUpSection.photographer.email",
    "saved-photographer@example.com",
  );
};

const contactFields = () => {
  const [submitterName, submitterEmail, photographerName, photographerEmail] =
    screen.getAllByPlaceholderText("Type here");
  return { submitterName, submitterEmail, photographerName, photographerEmail };
};

const expectRestoredValuesAndPrefill = () => {
  const fields = contactFields();
  expect(fields.submitterName).toHaveValue("Saved Submitter");
  expect(fields.submitterEmail).toHaveValue(currentUser.email);
  expect(fields.photographerName).toHaveValue(currentUser.displayName);
  expect(fields.photographerEmail).toHaveValue(
    "saved-photographer@example.com",
  );
};

describe("report contact restoration and prefill", () => {
  beforeEach(() => {
    localStorage.clear();
    useSiteSettings.mockReturnValue({
      data: { isHuman: true },
      isLoading: false,
      error: null,
    });
    saveContactValues();
  });

  afterEach(() => {
    localStorage.clear();
    jest.clearAllMocks();
  });

  test("restored values win when the signed-in user is available immediately", () => {
    renderWithProviders(<ReportEncounter currentUser={currentUser} />, true);

    expectRestoredValuesAndPrefill();
  });

  test("restored values win when the signed-in user arrives later", () => {
    const DeferredCurrentUserReport = () => {
      const [loadedUser, setLoadedUser] = React.useState();

      return (
        <>
          <button type="button" onClick={() => setLoadedUser(currentUser)}>
            Load current user
          </button>
          <ReportEncounter currentUser={loadedUser} />
        </>
      );
    };

    renderWithProviders(<DeferredCurrentUserReport />, true);

    const beforeUserLoads = contactFields();
    expect(beforeUserLoads.submitterName).toHaveValue("Saved Submitter");
    expect(beforeUserLoads.submitterEmail).toHaveValue("");
    expect(beforeUserLoads.photographerName).toHaveValue("");
    expect(beforeUserLoads.photographerEmail).toHaveValue(
      "saved-photographer@example.com",
    );

    fireEvent.click(screen.getByRole("button", { name: "Load current user" }));

    expectRestoredValuesAndPrefill();
  });
});
