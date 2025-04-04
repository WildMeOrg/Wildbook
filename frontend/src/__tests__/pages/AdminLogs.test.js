import React from "react";
import { screen } from "@testing-library/react";
import { renderWithProviders } from "../../utils/utils";
import AdminLogs from "../../pages/AdminLogs";

jest.mock("../../components/Card", () => ({ title, link }) => {
  const mockComponent = () => (
    <div data-testid="log-card">
      <div>{title}</div>
      <div>{link}</div>
    </div>
  );
  mockComponent.displayName = "Card";
  return mockComponent;
});

describe("AdminLogs", () => {
  test("renders the Logs heading", () => {
    renderWithProviders(<AdminLogs />);
    expect(screen.getByText("Logs")).toBeInTheDocument();
  });

  test("renders four log cards", () => {
    renderWithProviders(<AdminLogs />);
    const cards = screen.getAllByTestId("log-card");
    expect(cards).toHaveLength(4);
  });

  test("renders correct log titles", () => {
    renderWithProviders(<AdminLogs />);
    expect(screen.getByText("User Access Log")).toBeInTheDocument();
    expect(screen.getByText("Encounter Submission Log")).toBeInTheDocument();
    expect(screen.getByText("Deleted Encounters Log")).toBeInTheDocument();
    expect(screen.getByText("Email Log")).toBeInTheDocument();
  });

  test("cards have correct links", () => {
    renderWithProviders(<AdminLogs />);
    expect(
      screen.getByText("/wildbook_data_dir/logs/user-access.htm"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("/wildbook_data_dir/logs/encounter-submission.htm"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("/wildbook_data_dir/logs/encounter-delete.htm"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("/wildbook_data_dir/logs/email.htm"),
    ).toBeInTheDocument();
  });
});
