import React from "react";
import { render, screen } from "@testing-library/react";
import { BrowserRouter } from "react-router-dom";
import UnAuthenticatedSwitch from "../../UnAuthenticatedSwitch";

jest.mock("../../components/UnAuthenticatedAppHeader", () => () => {
  const mockComponent = () => (
    <div data-testid="unauth-header">UnAuthenticatedAppHeader</div>
  );
  mockComponent.displayName = "UnAuthenticatedAppHeader";
  return mockComponent;
});

jest.mock("../../components/Footer", () => {
  const FooterMock = () => <div data-testid="footer">Footer</div>;
  FooterMock.displayName = "Footer";
  return FooterMock;
});

jest.mock("../../pages/Login", () => {
  const LoginMock = () => <div>Login Page</div>;
  LoginMock.displayName = "Login";
  return LoginMock;
});

jest.mock("../../pages/Citation", () => {
  const CitationMock = () => <div>Citation Page</div>;
  CitationMock.displayName = "Citation";
  return CitationMock;
});

jest.mock("../../pages/errorPages/Unauthorized", () => {
  const UnauthorizedMock = ({ setHeader }) => {
    setHeader(false);
    return <div>Unauthorized Page</div>;
  };
  UnauthorizedMock.displayName = "Unauthorized";
  return UnauthorizedMock;
});

jest.mock("../../pages/ReportsAndManagamentPages/ReportEncounter", () => {
  const ReportEncounterMock = () => <div>Report Encounter Page</div>;
  ReportEncounterMock.displayName = "ReportEncounter";
  return ReportEncounterMock;
});

jest.mock("../../pages/ReportsAndManagamentPages/ReportConfirm", () => {
  const ReportConfirmMock = () => <div>Report Confirm Page</div>;
  ReportConfirmMock.displayName = "ReportConfirm";
  return ReportConfirmMock;
});

describe("UnAuthenticatedSwitch", () => {
  const renderComponent = (props) => {
    return render(
      <BrowserRouter>
        <UnAuthenticatedSwitch {...props} />
      </BrowserRouter>,
    );
  };

  test("renders header, main content, and footer", async () => {
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.getByTestId("footer")).toBeInTheDocument();
  });

  test("renders the login page by default", async () => {
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.getByText("Login Page")).toBeInTheDocument();
  });

  test("renders the citation page when navigating to /citation", async () => {
    window.history.pushState({}, "", "/citation");
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.findByText("Citation Page")).toBeInTheDocument();
  });

  test("navigates to login with redirect query when visiting an unknown page", async () => {
    window.history.pushState({}, "", "/unknown-page");
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(window.location.search).toMatch(/redirect=%2Funknown-page/);
  });
});
