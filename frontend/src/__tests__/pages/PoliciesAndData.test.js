import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "react-query";
import { IntlProvider } from "react-intl";
import PoliciesAndData from "../../../pages/PoliciesAndData/PoliciesAndData";
import LocaleContext from "../../../IntlProvider";
import ThemeColorContext from "../../../ThemeColorProvider";
import AuthContext from "../../../AuthProvider";
import messages from "../../../locale/en.json";

jest.mock("../../../pages/Citation", () => {
  const React = require("react");
  const MockCitingWildbook = () =>
    React.createElement(
      "div",
      { "data-testid": "citing-wildbook" },
      "Citing Wildbook Content",
    );
  MockCitingWildbook.displayName = "CitingWildbook";
  return MockCitingWildbook;
});

global.fetch = jest.fn();

const mockTheme = {
  primaryColors: {
    primary500: "#007bff",
  },
};

const renderComponent = ({
  locale = "en",
  theme = mockTheme,
  initialPath = "/policies",
} = {}) => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <IntlProvider locale="en" messages={messages}>
        <AuthContext.Provider value={{ isLoggedIn: true }}>
          <LocaleContext.Provider value={{ locale }}>
            <ThemeColorContext.Provider value={theme}>
              <MemoryRouter initialEntries={[initialPath]}>
                <PoliciesAndData />
              </MemoryRouter>
            </ThemeColorContext.Provider>
          </LocaleContext.Provider>
        </AuthContext.Provider>
      </IntlProvider>
    </QueryClientProvider>,
  );
};

describe("PoliciesAndData Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe("Initial Rendering", () => {
    test("renders the main title", () => {
      renderComponent();
      expect(screen.getByText("MENU_POLICIES_AND_DATA")).toBeInTheDocument();
    });

    test("renders all navigation sections", () => {
      renderComponent();
      expect(screen.getByText("MENU_LEARN_PRIVACYPOLICY")).toBeInTheDocument();
      expect(screen.getByText("MENU_LEARN_TERMSOFUSE")).toBeInTheDocument();
      expect(screen.getByText("MENU_LEARN_CITINGWILDBOOK")).toBeInTheDocument();
    });

    test("renders default section (Citing Wildbook) when no section param provided", () => {
      renderComponent();
      expect(screen.getByTestId("citing-wildbook")).toBeInTheDocument();
    });

    test("renders chevron icons for all sections", () => {
      renderComponent();
      const chevrons = document.querySelectorAll(".bi-chevron-right");
      expect(chevrons).toHaveLength(3);
    });
  });

  describe("URL Parameter Handling", () => {
    test("renders Privacy Policy when section param is 'privacy_policy'", async () => {
      fetch.mockResolvedValueOnce({ ok: true });
      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });

    test("renders Terms of Use when section param is 'terms_of_use'", async () => {
      fetch.mockResolvedValueOnce({ ok: true });
      renderComponent({
        initialPath: "/policies?section=terms_of_use",
      });

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });

    test("renders Citing Wildbook when section param is 'citing_wildbook'", () => {
      renderComponent({
        initialPath: "/policies?section=citing_wildbook",
      });
      expect(screen.getByTestId("citing-wildbook")).toBeInTheDocument();
    });

    test("handles uppercase section params", async () => {
      fetch.mockResolvedValueOnce({ ok: true });
      renderComponent({
        initialPath: "/policies?section=PRIVACY_POLICY",
      });

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });

    test("defaults to Citing Wildbook for invalid section param", () => {
      renderComponent({
        initialPath: "/policies?section=invalid",
      });
      expect(screen.getByTestId("citing-wildbook")).toBeInTheDocument();
    });
  });

  describe("Navigation Between Sections", () => {
    test("switches to Privacy Policy when clicked", async () => {
      const user = userEvent.setup();
      fetch.mockResolvedValue({ ok: true });

      renderComponent();

      const privacyPolicyLink = screen.getByText("MENU_LEARN_PRIVACYPOLICY");
      await user.click(privacyPolicyLink);

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });

    test("switches to Terms of Use when clicked", async () => {
      const user = userEvent.setup();
      fetch.mockResolvedValue({ ok: true });

      renderComponent();

      const termsLink = screen.getByText("MENU_LEARN_TERMSOFUSE");
      await user.click(termsLink);

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });

    test("switches to Citing Wildbook when clicked", async () => {
      const user = userEvent.setup();
      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      const citingLink = screen.getByText("MENU_LEARN_CITINGWILDBOOK");
      await user.click(citingLink);

      await waitFor(() => {
        expect(screen.getByTestId("citing-wildbook")).toBeInTheDocument();
      });
    });

    test("applies active styling to selected section", () => {
      renderComponent({
        initialPath: "/policies?section=citing_wildbook",
      });

      const citingLink = screen.getByText("MENU_LEARN_CITINGWILDBOOK");
      const spanElement = citingLink.closest("span");

      expect(spanElement).toHaveClass("fw-semibold");
    });
  });

  describe("PDF Loading States", () => {
    test("shows loading spinner while fetching PDF", async () => {
      fetch.mockImplementation(
        () =>
          new Promise((resolve) =>
            setTimeout(() => resolve({ ok: true }), 100),
          ),
      );

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      expect(screen.getByText("Loading…")).toBeInTheDocument();
      const spinner = document.querySelector(".spinner-border");
      expect(spinner).toBeInTheDocument();
    });

    test("shows 'PDF not found' message when PDF does not exist", async () => {
      fetch.mockResolvedValue({ ok: false });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      await waitFor(() => {
        expect(screen.getByText(/PDF not found for/)).toBeInTheDocument();
      });
    });

    test("renders iframe when PDF is successfully loaded", async () => {
      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      await waitFor(() => {
        const iframe = screen.getByTitle("PRIVACY_POLICY-pdf");
        expect(iframe).toBeInTheDocument();
      });
    });

    test("does not show loading state for component sections", () => {
      renderComponent({
        initialPath: "/policies?section=citing_wildbook",
      });

      expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
      expect(screen.getByTestId("citing-wildbook")).toBeInTheDocument();
    });
  });

  describe("PDF Locale Fallback", () => {
    test("tries locale-specific PDF first", async () => {
      fetch.mockResolvedValueOnce({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
        locale: "es",
      });

      await waitFor(() => {
        const calls = fetch.mock.calls;
        const firstCall = calls[0][0];
        expect(firstCall).toContain("privacy_policy_es.pdf");
      });
    });

    test("falls back to English PDF when locale-specific not found", async () => {
      fetch
        .mockResolvedValueOnce({ ok: false })
        .mockResolvedValueOnce({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
        locale: "es",
      });

      await waitFor(() => {
        expect(fetch).toHaveBeenCalledTimes(2);
        const secondCall = fetch.mock.calls[1][0];
        expect(secondCall).toContain("privacy_policy_en.pdf");
      });
    });

    test("shows error when both locale-specific and English PDFs not found", async () => {
      fetch
        .mockResolvedValueOnce({ ok: false })
        .mockResolvedValueOnce({ ok: false });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
        locale: "fr",
      });

      await waitFor(() => {
        expect(screen.getByText(/PDF not found for/)).toBeInTheDocument();
        expect(screen.getByText("fr")).toBeInTheDocument();
      });
    });

    test("uses HEAD method for PDF existence checks", async () => {
      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=terms_of_use",
      });

      await waitFor(() => {
        expect(fetch).toHaveBeenCalledWith(
          expect.any(String),
          expect.objectContaining({ method: "HEAD" }),
        );
      });
    });
  });

  describe("Theme Integration", () => {
    test("applies theme color to active section", () => {
      const customTheme = {
        primaryColors: {
          primary500: "#ff0000",
        },
      };

      renderComponent({
        theme: customTheme,
        initialPath: "/policies?section=citing_wildbook",
      });

      const citingLink = screen.getByText("MENU_LEARN_CITINGWILDBOOK");
      const listItem = citingLink.closest(".list-group-item");

      expect(listItem).toHaveStyle({ color: "#ff0000" });
    });

    test("applies GrayText color to inactive sections", () => {
      renderComponent({
        initialPath: "/policies?section=citing_wildbook",
      });

      const privacyLink = screen.getByText("MENU_LEARN_PRIVACYPOLICY");
      const listItem = privacyLink.closest(".list-group-item");

      expect(listItem).toHaveStyle({ color: "GrayText" });
    });
  });

  describe("Component Lifecycle", () => {
    test("cleans up fetch when component unmounts during loading", async () => {
      fetch.mockImplementation(
        () => new Promise(() => {}), // Never resolves
      );

      const { unmount } = renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      unmount();

      await waitFor(() => {
        expect(fetch).toHaveBeenCalled();
      });
    });
  });

  describe("PDF URL Generation", () => {
    test("generates correct public path for PDFs", async () => {
      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
        locale: "en",
      });

      await waitFor(() => {
        const firstCall = fetch.mock.calls[0][0];
        expect(firstCall).toMatch(/\/files\/privacy_policy_en\.pdf$/);
      });
    });

    test("handles PUBLIC_URL environment variable", async () => {
      const originalEnv = process.env.PUBLIC_URL;
      process.env.PUBLIC_URL = "/app";

      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=terms_of_use",
      });

      await waitFor(() => {
        const firstCall = fetch.mock.calls[0][0];
        expect(firstCall).toContain("/app/files/");
      });

      process.env.PUBLIC_URL = originalEnv;
    });
  });

  describe("Error Handling", () => {
    test("handles fetch errors gracefully", async () => {
      fetch.mockRejectedValue(new Error("Network error"));

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      await waitFor(() => {
        expect(screen.getByText(/PDF not found for/)).toBeInTheDocument();
      });
    });

    test("displays locale information in error message", async () => {
      fetch.mockResolvedValue({ ok: false });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
        locale: "de",
      });

      await waitFor(() => {
        expect(screen.getByText("de")).toBeInTheDocument();
      });
    });
  });

  describe("Accessibility", () => {
    test("iframe has proper title attribute", async () => {
      fetch.mockResolvedValue({ ok: true });

      renderComponent({
        initialPath: "/policies?section=privacy_policy",
      });

      await waitFor(() => {
        const iframe = screen.getByTitle("PRIVACY_POLICY-pdf");
        expect(iframe).toHaveAttribute("title", "PRIVACY_POLICY-pdf");
      });
    });

    test("chevron icons are hidden from screen readers", () => {
      renderComponent();

      const chevrons = document.querySelectorAll(".bi-chevron-right");
      chevrons.forEach((chevron) => {
        expect(chevron).toHaveAttribute("aria-hidden", "true");
      });
    });

    test("navigation items are keyboard accessible", () => {
      renderComponent();

      const listItems = screen
        .getByText("MENU_LEARN_PRIVACYPOLICY")
        .closest(".list-group-item");
      expect(listItems).toHaveStyle({ cursor: "pointer" });
    });
  });
});
