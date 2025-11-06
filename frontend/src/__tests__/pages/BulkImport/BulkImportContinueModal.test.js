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
});
