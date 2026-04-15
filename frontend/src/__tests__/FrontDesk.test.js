import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import axios from "axios";
import FrontDesk from "../FrontDesk";
import useGetSiteSettings from "../models/useGetSiteSettings";

jest.mock("../AuthenticatedSwitch", () => {
  const MockComponent = () => <div>Authenticated</div>;
  MockComponent.displayName = "MockAuthenticatedSwitch";
  return MockComponent;
});

jest.mock("../UnAuthenticatedSwitch", () => {
  const MockComponent = () => <div>Unauthenticated</div>;
  MockComponent.displayName = "MockUnAuthenticatedSwitch";
  return MockComponent;
});

jest.mock("../components/LoadingScreen", () => {
  const MockComponent = () => <div>Loading...</div>;
  MockComponent.displayName = "MockLoadingScreen";
  return MockComponent;
});

jest.mock("../GoogleTagManager", () => {
  const MockComponent = () => <div>GTM</div>;
  MockComponent.displayName = "MockGoogleTagManager";
  return MockComponent;
});

jest.mock("../components/SessionWarning", () => {
  const MockComponent = () => <div>SessionWarning</div>;
  MockComponent.displayName = "MockSessionWarning";
  return MockComponent;
});

jest.mock("../hooks/useDocumentTitle", () => jest.fn());
jest.mock("../models/useGetSiteSettings");
jest.mock("../models/notifications/getMergeNotifications", () => jest.fn());
jest.mock("../models/notifications/getCollaborationNotifications", () =>
  jest.fn(),
);

jest.mock("axios");

describe("FrontDesk Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useGetSiteSettings.mockReturnValue({ data: { showClassicSubmit: false } });
  });

  test("renders UnauthenticatedSwitch if user is not logged in (401)", async () => {
    axios.head.mockRejectedValueOnce({ response: { status: 401 } });

    render(<FrontDesk />);

    await waitFor(() => {
      expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    });

    expect(screen.getByText("GTM")).toBeInTheDocument();
    expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
  });

  test("keeps loading on non-401 login check failure (current behavior)", async () => {
    const warnSpy = jest.spyOn(console, "warn").mockImplementation(() => {});
    axios.head.mockRejectedValueOnce(new Error("Network Error"));
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(<FrontDesk />);

    await waitFor(() => {
      expect(screen.getByText("Loading...")).toBeInTheDocument();
    });

    expect(screen.queryByText("Unauthenticated")).not.toBeInTheDocument();
    expect(warnSpy).toHaveBeenCalled();

    warnSpy.mockRestore();
  });
});
