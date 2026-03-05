import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import CreateNewIndividualModal from "../../../pages/MatchResultsPage/components/CreateNewIndividualModal";

const themeColor = {
  primaryColors: { primary500: "#00ACCE" },
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
    <IntlProvider locale="en" messages={{}}>
      <CreateNewIndividualModal {...defaultProps} {...props} />
    </IntlProvider>,
  );

beforeEach(() => {
  globalThis.fetch = jest.fn(() =>
    Promise.resolve({ json: () => Promise.resolve({ success: false }) }),
  );
});

describe("CreateNewIndividualModal", () => {
  test("does not render when show is false", () => {
    renderModal({ show: false });
    expect(screen.queryByText("CREATE_NEW_INDIVIDUAL")).not.toBeInTheDocument();
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
    expect(onNameChange).toHaveBeenCalledWith("Luna");
  });

  test("Cancel button calls onHide", () => {
    const onHide = jest.fn();
    renderModal({ onHide });
    fireEvent.click(screen.getByText("CANCEL"));
    expect(onHide).toHaveBeenCalled();
  });

  test("fetches suggested ID when show=true and locationId provided", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ success: true, nextName: "ID-007" }],
        }),
    });
    renderModal({ locationId: "loc-1" });
    await waitFor(() => {
      expect(globalThis.fetch).toHaveBeenCalledWith(
        expect.stringContaining("next_name?locationId=loc-1"),
      );
    });
  });

  test("shows suggested ID and USE_THIS button after fetch", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ success: true, nextName: "ID-042" }],
        }),
    });
    renderModal({ locationId: "loc-2" });
    expect(await screen.findByText(/ID-042/)).toBeInTheDocument();
    expect(screen.getByText("USE_THIS")).toBeInTheDocument();
  });

  test("clicking USE_THIS calls onNameChange with suggested ID", async () => {
    globalThis.fetch = jest.fn().mockResolvedValueOnce({
      json: () =>
        Promise.resolve({
          success: true,
          results: [{ success: true, nextName: "ID-099" }],
        }),
    });
    const onNameChange = jest.fn();
    renderModal({ locationId: "loc-3", onNameChange });
    await screen.findByText("USE_THIS");
    fireEvent.click(screen.getByText("USE_THIS"));
    expect(onNameChange).toHaveBeenCalledWith("ID-099");
  });
});
