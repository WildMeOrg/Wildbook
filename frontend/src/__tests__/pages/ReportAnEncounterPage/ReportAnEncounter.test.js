import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { renderWithProviders } from "../../../utils/utils";
import ReportEncounter from "../../../pages/ReportsAndManagamentPages/ReportEncounter";

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: jest.fn(() => ({
    data: { procaptchaSiteKey: "mock-key", isHuman: true },
  })),
}));

jest.mock("../../../components/BrutalismButton", () => {
  const MockBrutalismButton = ({ children, onClick }) => (
    <button data-testid="brutalism-button" onClick={onClick}>
      {children}
    </button>
  );
  MockBrutalismButton.displayName = "MockBrutalismButton";
  return MockBrutalismButton;
});

jest.mock("../../../components/MainButton", () => {
  const mockComponent = ({ children, onClick }) => (
    <button data-testid="main-button" onClick={onClick}>
      {children}
    </button>
  );
  mockComponent.displayName = "MockMainButton";
  return mockComponent;
});

jest.mock("../../../pages/ReportsAndManagamentPages/ImageSection", () => ({
  __esModule: true,
  ImageSection: () => <div data-testid="image-section" />,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/DateTimeSection", () => ({
  __esModule: true,
  DateTimeSection: () => <div data-testid="date-time-section" />,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/PlaceSection", () => ({
  __esModule: true,
  PlaceSection: () => <div data-testid="place-section" />,
}));
jest.mock("../../../components/AdditionalCommentsSection", () => ({
  __esModule: true,
  AdditionalCommentsSection: () => <div data-testid="comments-section" />,
}));
jest.mock("../../../pages/ReportsAndManagamentPages/SpeciesSection", () => ({
  __esModule: true,
  ReportEncounterSpeciesSection: () => <div data-testid="species-section" />,
}));
jest.mock("../../../components/FollowUpSection", () => ({
  __esModule: true,
  FollowUpSection: () => <div data-testid="followup-section" />,
}));

jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore");

const renderComponent = (storeOverrides = {}) => {
  const mockStore = {
    setIsHumanLocal: jest.fn(),
    setShowSubmissionFailedAlert: jest.fn(),
    validateFields: jest.fn(),
    submitReport: jest.fn(),
    finished: false,
    success: false,
    showSubmissionFailedAlert: false,
    isHumanLocal: true,
    error: null,
    ...storeOverrides,
  };

  return renderWithProviders(<ReportEncounter store={mockStore} />);
};

describe("ReportEncounter Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders the component correctly", () => {
    renderComponent();

    expect(screen.getByText("REPORT_AN_ENCOUNTER")).toBeInTheDocument();
    expect(screen.getByTestId("image-section")).toBeInTheDocument();
    expect(screen.getByTestId("date-time-section")).toBeInTheDocument();
    expect(screen.getByTestId("place-section")).toBeInTheDocument();
    expect(screen.getByTestId("comments-section")).toBeInTheDocument();
    expect(screen.getByTestId("followup-section")).toBeInTheDocument();
    expect(screen.getByTestId("species-section")).toBeInTheDocument();
  });

  test("displays missing required fields message when validation fails", async () => {
    const store = {
      validateFields: jest.fn(() => false),
      setShowSubmissionFailedAlert: jest.fn(),
      submitReport: jest.fn(),
      showSubmissionFailedAlert: true,
      finished: false,
      success: false,
    };

    renderComponent(store);

    const submitButton = screen.getByTestId("main-button");
    screen.debug();
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText("MISSING_REQUIRED_FIELDS")).toBeInTheDocument();
      expect(store.setShowSubmissionFailedAlert).toHaveBeenCalledWith(true);
    });
  });

  test("submits the report successfully", async () => {
    const mockResponse = { someKey: "someValue" };

    const store = {
      validateFields: jest.fn(() => true),
      submitReport: jest.fn(() => Promise.resolve(mockResponse)),
      finished: true,
      success: true,
    };

    renderComponent(store);

    screen.debug();
    console.log(
      "+++++++++++++++++++++++++++++",
      screen.getByTestId("main-button"),
    );
    const submitButton = screen.getByTestId("main-button");
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(store.submitReport).toHaveBeenCalled();
    });
  });

  test("displays an error message when the submission fails", async () => {
    const store = {
      validateFields: jest.fn(() => true),
      submitReport: jest.fn(() => Promise.reject()),
      finished: true,
      success: false,
      setShowSubmissionFailedAlert: jest.fn(),
    };

    renderComponent(store);

    const submitButton = screen.getByTestId("main-button");
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(store.setShowSubmissionFailedAlert).toHaveBeenCalledWith(true);
      expect(screen.getByText("SUBMISSION_FAILED")).toBeInTheDocument();
    });
  });

  test("loads captcha correctly when user is not human", () => {
    const store = {
      isHumanLocal: false,
      setIsHumanLocal: jest.fn(),
    };

    renderComponent(store);

    expect(screen.getByText("LOGIN_SIGN_IN")).toBeInTheDocument();
  });

  test("does not load captcha when user is human", () => {
    const store = {
      isHumanLocal: true,
      setIsHumanLocal: jest.fn(),
    };

    renderComponent(store);

    expect(screen.queryByText("LOGIN_SIGN_IN")).not.toBeInTheDocument();
  });

  test("stores form data in localStorage before login", () => {
    global.localStorage.setItem = jest.fn();

    renderComponent();

    const loginButton = screen.getByText("Sign In");
    fireEvent.click(loginButton);

    expect(global.localStorage.setItem).toHaveBeenCalledWith(
      "species",
      expect.anything(),
    );
    expect(global.localStorage.setItem).toHaveBeenCalledWith(
      "followUpSection.submitter.name",
      expect.anything(),
    );
  });

  test("removes stored form data after component mounts", () => {
    global.localStorage.removeItem = jest.fn();

    renderComponent();

    expect(global.localStorage.removeItem).toHaveBeenCalledWith("species");
    expect(global.localStorage.removeItem).toHaveBeenCalledWith(
      "followUpSection.submitter.name",
    );
  });
});
