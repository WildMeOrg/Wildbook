import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import CalendarView from "../../../pages/SearchPages/searchResultTabs/CalendarView";

jest.mock("../../../components/FullScreenLoader", () => <div>Loading...</div>);

describe("CalendarView", () => {
  const mockStore = {
    searchResultsAll: [
      { id: "enc1", date: "2023-01-01T00:00:00Z" },
      { id: "enc2", date: "2023-01-02T00:00:00Z" },
    ],
    loadingAll: false,
  };

  it("renders without crashing", () => {
    render(<CalendarView store={mockStore} />);
    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });

  it("displays events in the calendar", async () => {
    render(<CalendarView store={mockStore} />);

    await waitFor(() => {
      expect(screen.queryByText("Loading...")).not.toBeInTheDocument();
      expect(screen.getByText("enc1")).toBeInTheDocument();
      expect(screen.getByText("enc2")).toBeInTheDocument();
    });
  });

  it("handles empty search results", () => {
    const emptyStore = { ...mockStore, searchResultsAll: [] };
    render(<CalendarView store={emptyStore} />);

    expect(screen.queryByText("enc1")).not.toBeInTheDocument();
    expect(screen.queryByText("enc2")).not.toBeInTheDocument();
  });

  it("shows loading state when loadingAll is true", () => {
    const loadingStore = { ...mockStore, loadingAll: true };
    render(<CalendarView store={loadingStore} />);

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });
});
