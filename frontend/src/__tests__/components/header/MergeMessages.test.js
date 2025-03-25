import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import MergeMessages from "../../../components/navBar/MergeMessages";
import changeIndividualMergeState from "../../../models/notifications/changeIndividualMergeState";
import { IntlProvider } from "react-intl";

jest.mock("../../../components/BrutalismButton", () => {
  const mockComponent = ({ onClick, children }) => (
    <button data-testid="brutalism-button" onClick={onClick}>
      {children}
    </button>
  );
  mockComponent.displayName = "BrutalismButton";
  return mockComponent;
});

jest.mock("../../../models/notifications/changeIndividualMergeState");

const mockGetAllNotifications = jest.fn();
const mockSetModalOpen = jest.fn();

const renderComponent = (mergeData) => {
  return render(
    <IntlProvider locale="en">
      <MergeMessages
        mergeData={mergeData}
        getAllNotifications={mockGetAllNotifications}
        setModalOpen={mockSetModalOpen}
      />
    </IntlProvider>,
  );
};

describe("MergeMessages Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders merge pending message correctly", () => {
    const mergeData = [
      {
        taskId: "task-123",
        notificationType: "mergePending",
        primaryIndividualName: "John Doe",
        secondaryIndividualName: "Jane Doe",
        initiator: "Admin",
        mergeExecutionDate: "2024-03-12",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    expect(screen.getByText("MERGE_PENDING_MESSAGE")).toBeInTheDocument();
  });

  test("renders merge complete message correctly", () => {
    const mergeData = [
      {
        taskId: "task-456",
        notificationType: "mergeComplete",
        primaryIndividualName: "Alice",
        secondaryIndividualName: "Bob",
        initiator: "Admin",
        mergeExecutionDate: "2024-03-12",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    expect(screen.getByText("MERGE_COMPLETE_MESSAGE")).toBeInTheDocument();
  });

  test("renders merge denied message correctly", () => {
    const mergeData = [
      {
        taskId: "task-789",
        notificationType: "mergeDenied",
        primaryIndividualName: "Charlie",
        secondaryIndividualName: "David",
        initiator: "Admin",
        deniedBy: "Moderator",
        mergeExecutionDate: "2024-03-12",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    expect(screen.getByText("MERGE_DENIED_MESSAGE")).toBeInTheDocument();
  });

  test("renders buttons for pending merge when not owned by user", () => {
    const mergeData = [
      {
        taskId: "task-111",
        notificationType: "mergePending",
        primaryIndividualName: "Eve",
        secondaryIndividualName: "Frank",
        initiator: "Admin",
        mergeExecutionDate: "2024-03-12",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    const buttons = screen.getAllByTestId("brutalism-button");
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toHaveTextContent("IGNORE");
    expect(buttons[1]).toHaveTextContent("DENY");
  });

  test("clicking dismiss on merge complete calls API", async () => {
    changeIndividualMergeState.mockResolvedValue({ status: 200 });

    const mergeData = [
      {
        taskId: "task-777",
        notificationType: "mergeComplete",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    const dismissButton = screen.getByText("DISMISS");
    fireEvent.click(dismissButton);

    await waitFor(() => {
      expect(changeIndividualMergeState).toHaveBeenCalledWith(
        "ignore",
        "task-777",
      );
      expect(mockGetAllNotifications).toHaveBeenCalled();
      expect(mockSetModalOpen).toHaveBeenCalledWith(false);
    });
  });

  test("clicking dismiss on merge denied calls API", async () => {
    changeIndividualMergeState.mockResolvedValue({ status: 200 });

    const mergeData = [
      {
        taskId: "task-888",
        notificationType: "mergeDenied",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    const dismissButton = screen.getByText("DISMISS");
    fireEvent.click(dismissButton);

    await waitFor(() => {
      expect(changeIndividualMergeState).toHaveBeenCalledWith(
        "ignore",
        "task-888",
      );
      expect(mockGetAllNotifications).toHaveBeenCalled();
      expect(mockSetModalOpen).toHaveBeenCalledWith(false);
    });
  });

  test("calls API and updates state on button click", async () => {
    changeIndividualMergeState.mockResolvedValue({ status: 200 });

    const mergeData = [
      {
        taskId: "task-222",
        notificationType: "mergePending",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    const denyButton = screen.getByText("DENY");
    fireEvent.click(denyButton);

    await waitFor(() => {
      expect(changeIndividualMergeState).toHaveBeenCalledWith(
        "deny",
        "task-222",
      );
      expect(mockGetAllNotifications).toHaveBeenCalled();
      expect(mockSetModalOpen).toHaveBeenCalledWith(false);
    });
  });

  test("displays error message when API call fails", async () => {
    changeIndividualMergeState.mockResolvedValue({ status: 500 });

    const mergeData = [
      {
        taskId: "task-333",
        notificationType: "mergePending",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    const ignoreButton = screen.getByText("IGNORE");
    fireEvent.click(ignoreButton);

    await waitFor(() => {
      expect(screen.getByText("BEERROR_UNKNOWN")).toBeInTheDocument();
    });
  });

  test("renders correct initiator message for current user", () => {
    const mergeData = [
      {
        taskId: "task-444",
        notificationType: "mergePending",
        initiator: "current user",
        ownedByMe: "true",
      },
    ];

    renderComponent(mergeData);

    expect(screen.getByText("INITIATED_BY_USER")).toBeInTheDocument();
    expect(screen.queryByTestId("brutalism-button")).not.toBeInTheDocument();
  });

  test("renders correct initiator message for another user", () => {
    const mergeData = [
      {
        taskId: "task-555",
        notificationType: "mergePending",
        initiator: "Another User",
        ownedByMe: "false",
      },
    ];

    renderComponent(mergeData);

    expect(screen.getByText("INITIATED_BY_USER")).toBeInTheDocument();
  });

  test("handles empty mergeData", () => {
    renderComponent([]);

    expect(
      screen.queryByText("INDIVIDUAL_MERGE_NOTIFICATIONS"),
    ).not.toBeInTheDocument();
  });
});
