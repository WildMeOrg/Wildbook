import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import InstructionsModal from "../../../pages/MatchResultsPage/components/InstructionsModal";

const themeColor = {
  primaryColors: { primary500: "#00ACCE" },
};

const renderModal = (props = {}) =>
  render(
    <IntlProvider locale="en" messages={{}}>
      <InstructionsModal
        show={true}
        onHide={jest.fn()}
        taskId="task-abc-123"
        themeColor={themeColor}
        {...props}
      />
    </IntlProvider>,
  );

describe("InstructionsModal", () => {
  test("does not render when show is false", () => {
    renderModal({ show: false });
    expect(
      screen.queryByText("MATCHING_PAGE_INSTRUCTIONS"),
    ).not.toBeInTheDocument();
  });

  test("renders modal title when show is true", () => {
    renderModal();
    expect(screen.getByText("MATCHING_PAGE_INSTRUCTIONS")).toBeInTheDocument();
  });

  test("displays the task ID", () => {
    renderModal({ taskId: "task-xyz" });
    expect(screen.getByText("task-xyz")).toBeInTheDocument();
  });

  test("shows dash when taskId is not provided", () => {
    renderModal({ taskId: null });
    expect(screen.getByText("-")).toBeInTheDocument();
  });

  test("copy button is disabled when taskId is null", () => {
    renderModal({ taskId: null });
    const copyBtn = screen.getByRole("button", { name: /copy/i });
    expect(copyBtn).toBeDisabled();
  });

  test("copy button is enabled when taskId is provided", () => {
    renderModal({ taskId: "task-123" });
    const copyBtn = screen.getByRole("button", { name: /copy/i });
    expect(copyBtn).not.toBeDisabled();
  });

  test("clicking copy button writes taskId to clipboard", async () => {
    Object.assign(navigator, {
      clipboard: { writeText: jest.fn().mockResolvedValue(undefined) },
    });
    renderModal({ taskId: "task-abc" });
    fireEvent.click(screen.getByRole("button", { name: /copy/i }));
    await waitFor(() => {
      expect(navigator.clipboard.writeText).toHaveBeenCalledWith("task-abc");
    });
  });

  test("renders section titles for scores, project, tools", () => {
    renderModal();
    expect(screen.getByText("SCORES")).toBeInTheDocument();
    expect(screen.getByText("PROJECT")).toBeInTheDocument();
    expect(screen.getByText("TOOLS")).toBeInTheDocument();
  });

  test("renders documentation link", () => {
    renderModal();
    const link = screen.getByText("WILDBOOK_DOCUMENTATION");
    expect(link.tagName).toBe("A");
    expect(link.getAttribute("target")).toBe("_blank");
  });
});
