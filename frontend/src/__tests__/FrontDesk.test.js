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

jest.mock("axios");

describe("FrontDesk Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders UnauthenticatedSwitch if user is not logged in", async () => {
    axios.head.mockRejectedValueOnce({ response: { status: 401 } });
    useGetSiteSettings.mockReturnValue({ data: { showClassicSubmit: false } });

    render(<FrontDesk />);
    await waitFor(() => {
      expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    });
  });

  test("handles login status check failure gracefully", async () => {
    axios.head.mockRejectedValueOnce(new Error("Network Error"));
    useGetSiteSettings.mockReturnValue({ data: {} });

    render(<FrontDesk />);
    await waitFor(() => {
      expect(screen.getByText("Unauthenticated")).toBeInTheDocument();
    });
  });
});
