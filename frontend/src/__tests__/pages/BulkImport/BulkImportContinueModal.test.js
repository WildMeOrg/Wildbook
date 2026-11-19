import React from "react";
import { render, screen, fireEvent } from "@testing-library/react";
import BulkImportContinueModal from "../../../pages/BulkImport/BulkImportContinueModal";
import ThemeContext from "../../../ThemeColorProvider";

jest.mock("react-intl", () => ({
  FormattedMessage: ({ defaultMessage }) => <span>{defaultMessage}</span>,
}));

describe("BulkImportContinueModal", () => {
  const draftData = {
    submissionId: "abc-123",
    spreadsheetFileName: "my-report.xlsx",
    uploadedImages: [0, 1, 2, 3],
    lastSavedAt: 1620000000000,
  };

  let storeMock;
  let setRenderModeMock;

  beforeEach(() => {
    jest
      .spyOn(Storage.prototype, "getItem")
      .mockReturnValue(JSON.stringify(draftData));
    jest.spyOn(Storage.prototype, "removeItem");
    jest
      .spyOn(Date.prototype, "toLocaleString")
      .mockReturnValue("2021-05-03, 10:00 AM");
    delete window.location;
    window.location = { reload: jest.fn() };

    storeMock = {
      hydrate: jest.fn(),
      setActiveStep: jest.fn(),
      fetchAndApplyUploaded: jest.fn(),
      resetToDefaults: jest.fn(),
      resetSpreadsheet: jest.fn(),
    };
    setRenderModeMock = jest.fn();
  });

  afterEach(() => {
    jest.restoreAllMocks();
  });

  const renderModal = () =>
    render(
      <ThemeContext.Provider
        value={{
          primaryColors: { primary100: "#fff", primary500: "#000" },
          wildMeColors: { cyan700: "#0ff" },
          defaultColors: { white: "#fff" },
        }}
      >
        <BulkImportContinueModal
          store={storeMock}
          setRenderMode1={setRenderModeMock}
        />
      </ThemeContext.Provider>,
    );

  test("renders modal with draft summary", () => {
    renderModal();
    expect(screen.getByText("Resume Bulk Import")).toBeInTheDocument();
    expect(screen.getByText("my-report.xlsx")).toBeInTheDocument();
    expect(screen.getByText("4")).toBeInTheDocument();
    expect(screen.getByText("Images uploaded")).toBeInTheDocument();
    expect(
      screen.getByText("2021-05-03, 10:00 AM", { exact: false }),
    ).toBeInTheDocument();
  });

  test('clicking "Resume" continues the draft correctly', () => {
    renderModal();
    fireEvent.click(screen.getByText("Resume"));
    expect(setRenderModeMock).toHaveBeenCalledWith("list");
    expect(storeMock.hydrate).toHaveBeenCalledWith(draftData);
    expect(storeMock.setActiveStep).toHaveBeenCalledWith(0);
    expect(storeMock.fetchAndApplyUploaded).toHaveBeenCalled();
  });

  test('clicking "Start New Import" clears draft and reloads', () => {
    renderModal();
    fireEvent.click(screen.getByText("Start New Import"));
    expect(storeMock.resetToDefaults).toHaveBeenCalled();
    expect(localStorage.removeItem).toHaveBeenCalledWith("BulkImportStore");
    expect(window.location.reload).toHaveBeenCalled();
  });

  describe("boxed draft saved before photo slots were kept", () => {
    const A0 = "Encounter.mediaAsset0";
    const A2 = "Encounter.mediaAsset2";
    const legacy = {
      ...draftData,
      columnsDef: [A0, `${A0}.bbox`],
      rawColumns: [A0, A2, `${A0}.bbox`],
      spreadsheetData: [{ [A0]: "a.jpg, c.jpg" }],
      rawData: [{ [A0]: "a.jpg", [A2]: "c.jpg" }],
    };
    const edited = {
      ...legacy,
      rawData: [{ [A0]: "a.jpg, c.jpg", [A2]: "c.jpg" }],
    };
    const REUPLOAD = /must be uploaded again/;

    [
      ["untouched", legacy],
      ["edited", edited],
    ].forEach(([name, draft]) => {
      test(`${name}: asks for a re-upload and drops the saved spreadsheet`, () => {
        Storage.prototype.getItem.mockReturnValue(JSON.stringify(draft));
        renderModal();
        expect(screen.getByText(REUPLOAD)).toBeInTheDocument();
        fireEvent.click(screen.getByText("Resume"));
        expect(storeMock.hydrate).toHaveBeenCalledWith(draft);
        expect(storeMock.resetSpreadsheet).toHaveBeenCalledTimes(1);
        expect(storeMock.hydrate.mock.invocationCallOrder[0]).toBeLessThan(
          storeMock.resetSpreadsheet.mock.invocationCallOrder[0],
        );
      });
    });

    test("an ordinary draft resumes as before", () => {
      renderModal();
      expect(screen.queryByText(REUPLOAD)).not.toBeInTheDocument();
      fireEvent.click(screen.getByText("Resume"));
      expect(storeMock.hydrate).toHaveBeenCalled();
      expect(storeMock.resetSpreadsheet).not.toHaveBeenCalled();
    });
  });
});
