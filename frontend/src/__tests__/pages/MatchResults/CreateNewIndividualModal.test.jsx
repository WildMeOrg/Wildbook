import React from "react";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  act,
} from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { toast } from "react-toastify";
import CreateNewIndividualModal from "../../../pages/MatchResultsPage/components/CreateNewIndividualModal";

// Use a stable, real intl context; the global mock returns a new object per render.
jest.unmock("react-intl");
jest.mock("react-toastify", () => ({
  toast: { error: jest.fn(), success: jest.fn() },
}));

const themeColor = {
  primaryColors: { primary500: "#00ACCE" },
};

const messages = {
  CREATE_NEW_INDIVIDUAL: "CREATE_NEW_INDIVIDUAL",
  CANCEL: "CANCEL",
  USE_THIS: "USE_THIS",
};

const defaultProps = {
  show: true,
  onHide: jest.fn(),
  encounterId: "enc-001",
  newIndividualName: "",
  onNameChange: jest.fn(),
  onConfirm: jest.fn(),
  loading: false,
  themeColor,
  identificationRemarks: ["AI-assisted", "Manual review"],
  locationId: "",
};

const renderModal = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <CreateNewIndividualModal {...defaultProps} {...props} />
    </IntlProvider>,
  );

beforeEach(() => {
  jest.clearAllMocks();
  globalThis.fetch = jest.fn(() => new Promise(() => {}));
});

describe("CreateNewIndividualModal", () => {
  test("does not render when show is false", () => {
    renderModal({ show: false });
    expect(screen.queryByText("CREATE_NEW_INDIVIDUAL")).not.toBeInTheDocument();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  test("renders modal title when show is true", () => {
    renderModal();
    expect(screen.getAllByText("CREATE_NEW_INDIVIDUAL")[0]).toBeInTheDocument();
  });

  test("displays encounter ID as a link", () => {
    renderModal({ encounterId: "enc-abc" });
    const link = screen.getByText("enc-abc");
    expect(link.tagName).toBe("A");
    expect(link.href).toContain("enc-abc");
  });

  test("renders identification remarks as dropdown options", () => {
    renderModal();
    expect(screen.getByText("AI-assisted")).toBeInTheDocument();
    expect(screen.getByText("Manual review")).toBeInTheDocument();
  });

  test("confirm button is disabled when name is empty", () => {
    renderModal({ newIndividualName: "" });
    const confirmBtn = screen
      .getAllByText("CREATE_NEW_INDIVIDUAL")
      .find((el) => el.closest("button"));
    expect(confirmBtn.closest("button")).toBeDisabled();
  });

  test("confirm button is enabled when name is provided", () => {
    renderModal({ newIndividualName: "Nemo" });
    const confirmBtn = screen
      .getAllByText("CREATE_NEW_INDIVIDUAL")
      .find((el) => el.closest("button"));
    expect(confirmBtn.closest("button")).not.toBeDisabled();
  });

  test("confirm button is disabled while loading", () => {
    renderModal({ newIndividualName: "Nemo", loading: true });
    const confirmBtn = screen
      .getAllByText("CREATE_NEW_INDIVIDUAL")
      .find((el) => el.closest("button"));
    expect(confirmBtn.closest("button")).toBeDisabled();
  });

  test("clicking confirm calls onConfirm with selected remark", () => {
    const onConfirm = jest.fn();
    renderModal({ newIndividualName: "Nemo", onConfirm });
    const select = screen.getByRole("combobox");
    fireEvent.change(select, { target: { value: "AI-assisted" } });
    fireEvent.click(
      screen
        .getAllByText("CREATE_NEW_INDIVIDUAL")
        .find((el) => el.closest("button"))
        .closest("button"),
    );
    expect(onConfirm).toHaveBeenCalledWith("AI-assisted");
  });

  test("name input change calls onNameChange", () => {
    const onNameChange = jest.fn();
    renderModal({ onNameChange });
    fireEvent.change(screen.getByPlaceholderText("Enter name"), {
      target: { value: "Luna" },
    });
    expect(onNameChange).toHaveBeenCalledWith("Luna", false);
  });

  test("CANCEL button calls onHide", () => {
    const onHide = jest.fn();
    renderModal({ onHide });
    fireEvent.click(screen.getByText("CANCEL"));
    expect(onHide).toHaveBeenCalled();
  });

  test("fetches suggested ID when show=true and locationId provided", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ type: "locationId", success: true, nextName: "ID-007" }],
        }),
    });
    renderModal({ locationId: "loc-1" });
    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining("next_name?locationId=loc-1"),
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
    });
  });

  test("shows suggested ID and USE_THIS button after fetch", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ type: "locationId", success: true, nextName: "ID-042" }],
        }),
    });
    renderModal({ locationId: "loc-2" });
    expect(await screen.findByText(/ID-042/)).toBeInTheDocument();
    expect(screen.getByText("USE_THIS")).toBeInTheDocument();
  });

  test("clicking USE_THIS calls onNameChange with suggested ID", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      ok: true,
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ type: "locationId", success: true, nextName: "ID-099" }],
        }),
    });
    const onNameChange = jest.fn();
    renderModal({ locationId: "loc-3", onNameChange });
    await screen.findByText("USE_THIS");
    fireEvent.click(screen.getByText("USE_THIS"));
    expect(onNameChange).toHaveBeenCalledWith("ID-099", true);
  });

  test.each(["", null, undefined])(
    "shows user suggestion without a location (%s)",
    async (locationId) => {
      globalThis.fetch.mockResolvedValue({
        ok: true,
        json: async () => ({
          success: true,
          results: [
            { type: "user", success: true, nextName: "42", nextNameKey: "IoT" },
          ],
        }),
      });
      const onNameChange = jest.fn();
      renderModal({ locationId, onNameChange, newIndividualName: "My name" });
      await screen.findByText(/Suggested ID: 42/);
      expect(globalThis.fetch).toHaveBeenCalledWith(
        "/api/v3/individuals/info/next_name",
        expect.objectContaining({ signal: expect.any(AbortSignal) }),
      );
      expect(screen.getByRole("textbox")).toHaveValue("My name");
      fireEvent.click(screen.getByText("USE_THIS"));
      expect(onNameChange).toHaveBeenCalledWith("42", false);
    },
  );

  test("skips an empty location suggestion and displays the user suggestion", async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        results: [
          { type: "locationId", success: true, nextName: null },
          { type: "user", success: true, nextName: "43" },
        ],
      }),
    });
    renderModal({ locationId: "location without prefix" });
    expect(await screen.findByText(/Suggested ID: 43/)).toBeInTheDocument();
    expect(globalThis.fetch.mock.calls[0][0]).toContain(
      "locationId=location%20without%20prefix",
    );
  });

  test("prefers the location suggestion when both names are available", async () => {
    globalThis.fetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        success: true,
        results: [
          { type: "locationId", success: true, nextName: "LOC-44" },
          { type: "user", success: true, nextName: "44" },
        ],
      }),
    });
    const onNameChange = jest.fn();
    renderModal({ locationId: "loc-1", onNameChange });
    fireEvent.click(await screen.findByText("USE_THIS"));
    expect(onNameChange).toHaveBeenCalledWith("LOC-44", true);
  });

  test.each([
    { success: true, results: [] },
    {
      success: true,
      results: [{ type: "locationId", success: true, nextName: null }],
    },
    { success: false },
  ])(
    "allows manual naming when no suggestion is available: %j",
    async (data) => {
      globalThis.fetch.mockResolvedValue({ ok: true, json: async () => data });
      const onNameChange = jest.fn();
      renderModal({ onNameChange });
      await waitFor(() =>
        expect(
          screen.queryByTestId("create-new-individual-suggested-id-loading"),
        ).not.toBeInTheDocument(),
      );
      expect(screen.queryByText("USE_THIS")).not.toBeInTheDocument();
      fireEvent.change(screen.getByRole("textbox"), {
        target: { value: "Custom" },
      });
      expect(onNameChange).toHaveBeenCalledWith("Custom", false);
    },
  );

  test("keeps manual naming available when the suggestion request fails", async () => {
    globalThis.fetch.mockResolvedValue({ ok: false, status: 500 });
    const onNameChange = jest.fn();
    renderModal({ onNameChange });
    await waitFor(() =>
      expect(toast.error).toHaveBeenCalledWith("Failed to load suggested ID"),
    );
    expect(screen.queryByText("USE_THIS")).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "Custom" },
    });
    expect(onNameChange).toHaveBeenCalledWith("Custom", false);
  });

  test("ignores a stale response after location changes and refreshes when reopened", async () => {
    let resolveOld;
    globalThis.fetch
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          success: true,
          results: [{ type: "locationId", success: true, nextName: "NEW-1" }],
        }),
      });
    const modal = (props) => (
      <IntlProvider locale="en" messages={messages}>
        <CreateNewIndividualModal {...defaultProps} {...props} />
      </IntlProvider>
    );
    const { rerender } = render(modal({ locationId: "old" }));
    const oldSignal = globalThis.fetch.mock.calls[0][1].signal;
    rerender(modal({ locationId: "new" }));
    expect(await screen.findByText(/NEW-1/)).toBeInTheDocument();
    expect(oldSignal.aborted).toBe(true);
    await act(async () =>
      resolveOld({
        ok: true,
        json: async () => ({
          success: true,
          results: [{ type: "locationId", success: true, nextName: "OLD-1" }],
        }),
      }),
    );
    expect(screen.queryByText(/OLD-1/)).not.toBeInTheDocument();
    rerender(modal({ show: false, locationId: "new" }));
    globalThis.fetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        results: [{ type: "locationId", success: true, nextName: "NEW-2" }],
      }),
    });
    rerender(modal({ locationId: "new" }));
    expect(await screen.findByText(/NEW-2/)).toBeInTheDocument();
  });
});
