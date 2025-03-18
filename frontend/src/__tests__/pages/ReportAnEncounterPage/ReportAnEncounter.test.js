
import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import ReportEncounter from "../../../pages/ReportsAndManagamentPages/ReportEncounter";
import useGetSiteSettings from "../../../models/useGetSiteSettings";

beforeEach(() => {
  jest.spyOn(Storage.prototype, "removeItem");
});

afterEach(() => {
  jest.restoreAllMocks();
});

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: jest.fn(() => ({
    data: { procaptchaSiteKey: "mock-key", isHuman: true },
  })),
}));

global.mockProCaptchaRender = jest.fn();
jest.mock(
  "https://js.prosopo.io/js/procaptcha.bundle.js",
  () => ({
    render: global.mockProCaptchaRender,
  }),
  { virtual: true }
);

jest.mock("../../../pages/ReportsAndManagamentPages/ImageSection", () => ({
  ImageSection: () => <div data-testid="image-section" />,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/DateTimeSection", () => ({
  DateTimeSection: () => <div data-testid="date-time-section" />,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/PlaceSection", () => ({
  PlaceSection: () => <div data-testid="place-section" />,
}));

jest.mock("../../../components/AdditionalCommentsSection", () => ({
  AdditionalCommentsSection: () => <div data-testid="comments-section" />,
}));

jest.mock("../../../pages/ReportsAndManagamentPages/SpeciesSection", () => ({
  ReportEncounterSpeciesSection: () => <div data-testid="species-section" />,
}));
jest.mock("../../../components/FollowUpSection", () => ({
  FollowUpSection: () => <div data-testid="followup-section" />,
}));

import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";

jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore", () => ({
  ReportEncounterStore: jest.fn().mockImplementation(() => ({
    setIsHumanLocal: jest.fn(),
    setShowSubmissionFailedAlert: jest.fn(),
    validateFields: jest.fn(() => true),
    submitReport: jest.fn().mockResolvedValue({ someKey: "someValue" }),
    finished: false,
    success: false,
    showSubmissionFailedAlert: false,
    isHumanLocal: false,
    error: null,
    setImageRequired: jest.fn(),
  })),
}));

const renderComponent = () => renderWithProviders(<ReportEncounter />, false);

describe("ReportEncounter Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useGetSiteSettings.mockImplementation(() => ({
      data: { procaptchaSiteKey: "mock-key", isHuman: true },
    }));
  });

  test("renders component correctly", () => {
    renderComponent();

    expect(screen.getByText("REPORT_AN_ENCOUNTER")).toBeInTheDocument();
    expect(screen.getByTestId("image-section")).toBeInTheDocument();
    expect(screen.getByTestId("date-time-section")).toBeInTheDocument();
    expect(screen.getByTestId("place-section")).toBeInTheDocument();
    expect(screen.getByTestId("comments-section")).toBeInTheDocument();
    expect(screen.getByTestId("followup-section")).toBeInTheDocument();
    expect(screen.getByTestId("species-section")).toBeInTheDocument();
  });

  test("displays missing required fields message on validation fail", async () => {
    ReportEncounterStore.mockImplementation(() => ({
      validateFields: jest.fn(() => false),
      showSubmissionFailedAlert: true,
      setShowSubmissionFailedAlert: jest.fn(),
      setIsHumanLocal: jest.fn(),
      submitReport: jest.fn().mockResolvedValue({ someKey: "someValue" }),
      finished: false,
      success: false,
      isHumanLocal: true,
      error: null,
      setImageRequired: jest.fn(),
    }));

    renderComponent();

    const submitButton = screen.getByRole("button", { name: /SUBMIT_ENCOUNTER/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText("MISSING_REQUIRED_FIELDS")).toBeInTheDocument();
    });
  });

  test("submit report successfully", async () => {
    const mockSubmitReport = jest.fn().mockResolvedValue({ id: 123 });
    ReportEncounterStore.mockImplementation(() => ({
      submitReport: mockSubmitReport,
      finished: true,
      success: true,
      setIsHumanLocal: jest.fn(),
      setShowSubmissionFailedAlert: jest.fn(),
      validateFields: jest.fn(() => true),
      showSubmissionFailedAlert: false,
      isHumanLocal: true,
      error: null,
      setImageRequired: jest.fn(),
    }));

    renderComponent();

    const submitButton = screen.getByRole("button", { name: /SUBMIT_ENCOUNTER/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(mockSubmitReport).toHaveBeenCalled();
    });
  });

  test("displays error message when submission fails", async () => {
    ReportEncounterStore.mockImplementation(() => ({
      validateFields: jest.fn(() => true),
      finished: true,
      success: false,
      setShowSubmissionFailedAlert: jest.fn(),
      showSubmissionFailedAlert: true,
      setIsHumanLocal: jest.fn(),
      submitReport: jest.fn().mockResolvedValue({ someKey: "someValue" }),
      isHumanLocal: true,
      error: null,
      setImageRequired: jest.fn(),
    }));

    renderComponent();

    const submitButton = screen.getByRole("button", { name: /SUBMIT_ENCOUNTER/i });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText("SUBMISSION_FAILED")).toBeInTheDocument();
    });
  });

  // test("renders captcha when user is not signed in", () => {
  //   ReportEncounterStore.mockImplementation(() => ({
  //     isHumanLocal: true,
  //     setIsHumanLocal: jest.fn(),
  //     validateFields: jest.fn(() => true),
  //     submitReport: jest.fn(),
  //     finished: false,
  //     success: false,
  //     setShowSubmissionFailedAlert: jest.fn(),
  //     showSubmissionFailedAlert: false,
  //     error: null,
  //     setImageRequired: jest.fn(),
  //   }));

  //   renderComponent();

  //   expect(screen.getByText("LOGIN_SIGN_IN")).toBeInTheDocument();
  // });

  test("does not render captcha when user is logged in", () => {
    ReportEncounterStore.mockImplementation(() => ({
      isHumanLocal: true,
      setIsHumanLocal: jest.fn(),
      validateFields: jest.fn(() => true),
      submitReport: jest.fn(),
      finished: false,
      success: false,
      setShowSubmissionFailedAlert: jest.fn(),
      showSubmissionFailedAlert: false,
      error: null,
      setImageRequired: jest.fn(),
    }));
    renderWithProviders(<ReportEncounter />, true);
    expect(document.getElementById("procaptcha-container")).not.toBeInTheDocument();
  });

  test("handles scrolling behavior", () => {
    renderComponent();

    fireEvent.scroll(window, { target: { scrollY: 200 } });

    expect(screen.getByText("REPORT_AN_ENCOUNTER")).toBeInTheDocument();
  });

  test("clicking menu item changes section", () => {
    Element.prototype.scrollIntoView = jest.fn();
    renderComponent();

    fireEvent.click(screen.getByText("DATETIME_SECTION"));

    expect(screen.getByText("DATETIME_SECTION")).toBeInTheDocument();
  });

  test("ensures localStorage values are set and cleared", () => {
    renderComponent();

    expect(localStorage.removeItem).toHaveBeenCalledWith("species");
    expect(localStorage.removeItem).toHaveBeenCalledWith("followUpSection.submitter.name");
    expect(localStorage.removeItem).toHaveBeenCalledWith("followUpSection.submitter.email");
  });

  test("renders CAPTCHA when user is not signed in", async () => {
    ReportEncounterStore.mockImplementation(() => ({
      isHumanLocal: false,
      setIsHumanLocal: jest.fn(),
      setImageRequired: jest.fn(),
      validateFields: jest.fn(() => true),
      submitReport: jest.fn(),
    }));

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText("LOGIN_SIGN_IN")).toBeInTheDocument();
      // expect(screen.getByTestId("procaptcha-container")).toBeInTheDocument();
      // expect(global.mockProCaptchaRender).toHaveBeenCalled();
    });
    await waitFor(() => {
      // expect(global.mockProCaptchaRender).toHaveBeenCalled();
    });
  });

  // test("handles CAPTCHA verification", async () => {
  //   global.fetch = jest.fn(() =>
  //     Promise.resolve({
  //       json: () => Promise.resolve({ valid: true }),
  //     })
  //   );

  //   renderComponent();

  //   await waitFor(() => {
  //     expect(global.fetch).toHaveBeenCalledWith("/ReCAPTCHA", expect.any(Object));
  //   });
  // });

  // test("handles CAPTCHA error", async () => {
  //   global.fetch = jest.fn(() => Promise.reject("Network error"));

  //   renderComponent();

  //   await waitFor(() => {
  //     expect(console.error).toHaveBeenCalled();
  //   });
  // });

});

