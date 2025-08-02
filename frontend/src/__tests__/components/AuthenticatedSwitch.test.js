import React from "react";
import { render, screen } from "@testing-library/react";
import { BrowserRouter } from "react-router-dom";
import AuthenticatedSwitch from "../../AuthenticatedSwitch";
import useGetMe from "../../models/auth/users/useGetMe";

jest.mock("../../models/auth/users/useGetMe");
jest.mock("react-konva", () => ({
  Stage: () => <div data-testid="stage">Mocked Stage</div>,
  Layer: () => <div data-testid="layer">Mocked Layer</div>,
  Rect: () => <div data-testid="rect">Mocked Rect</div>,
  Transformer: () => <div data-testid="transformer">Mocked Transformer</div>,
}));

jest.mock("../../components/AuthenticatedAppHeader", () => {
  const AuthenticatedAppHeaderMock = () => (
    <div data-testid="auth-header">AuthenticatedAppHeader</div>
  );
  AuthenticatedAppHeaderMock.displayName = "AuthenticatedAppHeader";
  return AuthenticatedAppHeaderMock;
});

jest.mock("../../components/Footer", () => {
  const FooterMock = () => <div data-testid="footer">Footer</div>;
  FooterMock.displayName = "Footer";
  return FooterMock;
});

jest.mock("../../pages/Profile", () => {
  const ProfileMock = () => <div>Profile Page</div>;
  ProfileMock.displayName = "Profile";
  return ProfileMock;
});

jest.mock("../../pages/Login", () => {
  const LoginMock = () => <div>Login Page</div>;
  LoginMock.displayName = "Login";
  return LoginMock;
});

jest.mock("../../pages/Home", () => {
  const HomeMock = () => <div>Home Page</div>;
  HomeMock.displayName = "Home";
  return HomeMock;
});

jest.mock("../../pages/errorPages/NotFound", () => {
  const NotFoundMock = ({ setHeader }) => {
    setHeader(false);
    return <div>Not Found Page</div>;
  };
  NotFoundMock.displayName = "NotFound";
  return NotFoundMock;
});

describe("AuthenticatedSwitch", () => {
  beforeEach(() => {
    useGetMe.mockReturnValue({
      data: { username: "testUser", imageURL: "test-avatar.png" },
    });
  });

  const renderComponent = (props) => {
    return render(
      <BrowserRouter>
        <AuthenticatedSwitch {...props} />
      </BrowserRouter>,
    );
  };

  test("renders header, main content, and footer", async () => {
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });

    expect(await screen.findByTestId("auth-header")).toBeInTheDocument();
    expect(screen.getByTestId("footer")).toBeInTheDocument();
  });

  test("renders the home page by default", async () => {
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.findByText("Home Page")).toBeInTheDocument();
  });

  test("renders the profile page when navigating to /profile", async () => {
    window.history.pushState({}, "", "/profile");
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.findByText("Profile Page")).toBeInTheDocument();
  });

  test("renders the login page when navigating to /login", async () => {
    window.history.pushState({}, "", "/login");
    renderComponent({ showAlert: false, setShowAlert: jest.fn() });
    expect(await screen.findByText("Login Page")).toBeInTheDocument();
  });

  test("renders the NotFound page when navigating to an unknown route and sets header to false", async () => {
    const setHeaderMock = jest.fn();
    window.history.pushState({}, "", "/random-page");
    renderComponent({
      showAlert: false,
      setShowAlert: jest.fn(),
      setHeader: setHeaderMock,
    });
    expect(await screen.findByText("Not Found Page")).toBeInTheDocument();
  });
});
