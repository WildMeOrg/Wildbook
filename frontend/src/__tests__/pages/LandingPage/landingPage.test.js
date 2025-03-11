import React from "react";
import { screen } from "@testing-library/react";
import Home from "../../../pages/Home";
import useGetHomePageInfo from "../../../models/useGetHomePageInfo";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../models/useGetHomePageInfo");
jest.mock("../../../components/svg/Logo", () => {
  const React = require("react");
  const mockLogo = () => React.createElement("div", { "data-testid": "logo" });
  mockLogo.displayName = "Logo";
  return mockLogo;
});
jest.mock("../../../components/home/LandingImage", () => {
  const React = require("react");
  const MockLandingImage = () =>
    React.createElement("div", { "data-testid": "landing-image" });
  MockLandingImage.displayName = "LandingImage";
  return MockLandingImage;
});
jest.mock("../../../components/home/LatestData", () => {
  const React = require("react");
  const MockLatestData = () =>
    React.createElement("div", { "data-testid": "latest-data" });
  MockLatestData.displayName = "LatestData";
  return MockLatestData;
});
jest.mock("../../../components/home/PickUpWhereYouLeft", () => {
  const React = require("react");
  const MockPickUp = () =>
    React.createElement("div", { "data-testid": "pick-up" });
  MockPickUp.displayName = "PickUp";
  return MockPickUp;
});
jest.mock("../../../components/home/Report", () => {
  const React = require("react");
  const MockReport = () =>
    React.createElement("div", { "data-testid": "report" });
  MockReport.displayName = "Report";
  return MockReport;
});
jest.mock("../../../components/home/Projects", () => {
  const React = require("react");
  const MockProjects = () =>
    React.createElement("div", { "data-testid": "projects" });
  MockProjects.displayName = "Projects";
  return MockProjects;
});
jest.mock("../../../pages/errorPages/NotFound", () => {
  const React = require("react");
  const MockNotFound = () =>
    React.createElement("div", { "data-testid": "not-found" });
  MockNotFound.displayName = "NotFound";
  return MockNotFound;
});
jest.mock("../../../pages/errorPages/ServerError", () => {
  const React = require("react");
  const MockServerError = () =>
    React.createElement("div", { "data-testid": "server-error" });
  MockServerError.displayName = "ServerError";
  return MockServerError;
});
jest.mock("../../../pages/errorPages/BadRequest", () => {
  const React = require("react");
  const MockBadRequest = () =>
    React.createElement("div", { "data-testid": "bad-request" });
  MockBadRequest.displayName = "BadRequest";
  return MockBadRequest;
});
jest.mock("../../../pages/errorPages/Unauthorized", () => {
  const React = require("react");
  const MockUnauthorized = () =>
    React.createElement("div", { "data-testid": "unauthorized" });
  MockUnauthorized.displayName = "Unauthorized";
  return MockUnauthorized;
});
jest.mock("../../../pages/errorPages/Forbidden", () => {
  const React = require("react");
  const MockForbidden = () =>
    React.createElement("div", { "data-testid": "forbidden" });
  MockForbidden.displayName = "Forbidden";
  return MockForbidden;
});

describe("Home Page", () => {
  test("renders the main components when API call is successful", () => {
    useGetHomePageInfo.mockReturnValue({
      data: {
        latestEncounters: [],
        user: { username: "test_user" },
        projects: [],
      },
      loading: false,
      statusCode: null,
    });

    renderWithProviders(<Home />);

    expect(screen.getByTestId("landing-image")).toBeInTheDocument();
    expect(screen.getByTestId("latest-data")).toBeInTheDocument();
    expect(screen.getByTestId("pick-up")).toBeInTheDocument();
    expect(screen.getByTestId("report")).toBeInTheDocument();
    expect(screen.getByTestId("projects")).toBeInTheDocument();
  });

  test("renders loading state while fetching data", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: true,
      statusCode: null,
    });

    renderWithProviders(<Home />);
    expect(screen.queryByText("LatestData")).not.toBeInTheDocument();
  });

  test("renders NotFound component on 404 error", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: false,
      statusCode: 404,
    });
    renderWithProviders(<Home />);
    expect(screen.getByTestId("not-found")).toBeInTheDocument();
  });

  test("renders ServerError component on 500 error", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: false,
      statusCode: 500,
    });
    renderWithProviders(<Home />);
    expect(screen.getByTestId("server-error")).toBeInTheDocument();
  });

  test("renders BadRequest component on 400 error", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: false,
      statusCode: 400,
    });
    renderWithProviders(<Home />);
    expect(screen.getByTestId("bad-request")).toBeInTheDocument();
  });

  test("renders Unauthorized component on 401 error", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: false,
      statusCode: 401,
    });
    renderWithProviders(<Home />);
    expect(screen.getByTestId("unauthorized")).toBeInTheDocument();
  });

  test("renders Forbidden component on 403 error", () => {
    useGetHomePageInfo.mockReturnValue({
      data: null,
      loading: false,
      statusCode: 403,
    });
    renderWithProviders(<Home />);
    expect(screen.getByTestId("forbidden")).toBeInTheDocument();
  });
});
