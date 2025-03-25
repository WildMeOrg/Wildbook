import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import CollaborationMessages from "../../../components/navBar/CollaborationMessages";

jest.mock("../../../components/BrutalismButton", () => {
  const MockBrutalismButton = ({ children, onClick }) => (
    <button data-testid="brutalism-button" onClick={onClick}>
      {children}
    </button>
  );
  MockBrutalismButton.displayName = "MockBrutalismButton";
  return MockBrutalismButton;
});

const mockGetAllNotifications = jest.fn();
const mockSetModalOpen = jest.fn();

const createMockCollaborationData = (
  id,
  username,
  email,
  accessType,
  buttonClasses,
) => {
  return {
    getAttribute: (attr) => {
      const attrs = {
        "data-username": username,
        id: id,
      };
      return attrs[attr] || null;
    },
    textContent: `${username} (${accessType}) ${email}`,
    querySelectorAll: (selector) => {
      if (selector === 'input[type="button"]') {
        return buttonClasses.map((cls) => ({
          getAttribute: (attr) => (attr === "class" ? cls : cls),
          value: cls === "approve" ? "Approve" : "Deny",
        }));
      }
      return [];
    },
  };
};

const mockCollaborationData = [
  createMockCollaborationData(
    "edit-123",
    "john_doe",
    "john@example.com",
    "view-only",
    ["approve", "deny"],
  ),
];

const renderComponent = () => {
  return render(
    <CollaborationMessages
      collaborationTitle="Test Collaboration"
      collaborationData={mockCollaborationData}
      getAllNotifications={mockGetAllNotifications}
      setModalOpen={mockSetModalOpen}
    />,
  );
};

describe("CollaborationMessages Component", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test("renders collaboration title", () => {
    renderComponent();
    expect(screen.getByText("Test Collaboration")).toBeInTheDocument();
  });

  test("renders user information correctly", () => {
    renderComponent();
    expect(
      screen.getByText("john_doe (View-Only) john@example.com"),
    ).toBeInTheDocument();
  });

  test("renders buttons correctly", () => {
    renderComponent();
    const buttons = screen.getAllByTestId("brutalism-button");
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toHaveTextContent("approve");
    expect(buttons[1]).toHaveTextContent("deny");
  });

  test("handles button click and API response correctly", async () => {
    global.fetch = jest.fn(() =>
      Promise.resolve({
        json: () => Promise.resolve({}),
      }),
    );

    renderComponent();

    const approveButton = screen.getByText("approve");

    fireEvent.click(approveButton);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        "/Collaborate?json=1&username=john_doe&approve=approve&actionForExisting=approve&collabId=123 :",
      );
      expect(mockGetAllNotifications).toHaveBeenCalled();
      expect(mockSetModalOpen).toHaveBeenCalledWith(false);
    });
  });

  test("displays error message on API failure", async () => {
    global.fetch = jest.fn(() =>
      Promise.resolve({
        json: () => Promise.resolve({ error: "Something went wrong" }),
      }),
    );

    renderComponent();

    const denyButton = screen.getByText("deny");

    fireEvent.click(denyButton);

    await waitFor(() => {
      expect(screen.getByText("Something went wrong")).toBeInTheDocument();
    });
  });

  test("collabString is constructed correctly", async () => {
    global.fetch = jest.fn(() =>
      Promise.resolve({
        json: () => Promise.resolve({}),
      }),
    );

    renderComponent();

    const approveButton = screen.getByText("approve");
    fireEvent.click(approveButton);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        "/Collaborate?json=1&username=john_doe&approve=approve&actionForExisting=approve&collabId=123 :",
      );
    });
  });

  test("handles missing email gracefully", () => {
    const dataWithoutEmail = [
      createMockCollaborationData("edit-456", "jane_doe", "", "edit", [
        "approve",
      ]),
    ];

    render(
      <CollaborationMessages
        collaborationTitle="Test Collaboration"
        collaborationData={dataWithoutEmail}
        getAllNotifications={mockGetAllNotifications}
        setModalOpen={mockSetModalOpen}
      />,
    );

    expect(screen.getByText("jane_doe (Edit)")).toBeInTheDocument();
  });

  test("handles empty collaborationData", () => {
    render(
      <CollaborationMessages
        collaborationTitle="Test Collaboration"
        collaborationData={[]}
        getAllNotifications={mockGetAllNotifications}
        setModalOpen={mockSetModalOpen}
      />,
    );

    expect(screen.getByText("Test Collaboration")).toBeInTheDocument();
  });
});
