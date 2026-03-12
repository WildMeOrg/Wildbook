import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import CalendarView from "../../../pages/SearchPages/searchResultTabs/CalendarView";

jest.mock("date-fns", () => ({
  format: jest.fn((d) => d.toISOString()),
  parse: jest.fn((str) => new Date(str)),
  startOfWeek: jest.fn((d) => d),
  getDay: jest.fn(() => 0),
}));

jest.mock("date-fns/locale/en-US", () => ({}));

jest.mock("react-big-calendar", () => ({
  Calendar: ({ events = [] }) => (
    <div data-testid="calendar">
      {events.map((e) => (
        <div key={e.id}>{e.title}</div>
      ))}
    </div>
  ),
  Views: { MONTH: "month" },
  dateFnsLocalizer: () => ({}),
}));

jest.mock("../../../components/FullScreenLoader", () => () => (
  <div>Loading...</div>
));

const mockRefetch = jest.fn();
const mockUseFilterEncounters = jest.fn();

jest.mock("../../../models/encounters/useFilterEncounters", () => ({
  __esModule: true,
  default: (...args) => mockUseFilterEncounters(...args),
}));

describe("CalendarView", () => {
  const mockStore = {
    formFilters: [],
    loadingAll: false,
  };

  beforeEach(() => {
    jest.clearAllMocks();
    mockRefetch.mockResolvedValue(undefined);
    mockUseFilterEncounters.mockReturnValue({
      data: { results: [] },
      loading: false,
      refetch: mockRefetch,
    });
  });

  it("renders without crashing", () => {
    render(<CalendarView store={mockStore} />);
    expect(screen.getByTestId("calendar")).toBeInTheDocument();
  });

  it("displays events in the calendar", async () => {
    mockUseFilterEncounters.mockReturnValue({
      data: {
        results: [
          { id: "enc1", date: "2023-01-01T00:00:00Z" },
          { id: "enc2", date: "2023-01-02T00:00:00Z" },
        ],
      },
      loading: false,
      refetch: mockRefetch,
    });

    render(<CalendarView store={mockStore} />);

    await waitFor(() => {
      expect(screen.getByText("enc1")).toBeInTheDocument();
      expect(screen.getByText("enc2")).toBeInTheDocument();
    });
  });

  it("handles empty search results", () => {
    render(<CalendarView store={mockStore} />);

    expect(screen.queryByText("enc1")).not.toBeInTheDocument();
    expect(screen.queryByText("enc2")).not.toBeInTheDocument();
  });

  it("shows loading state when loadingAll is true", () => {
    const loadingStore = { ...mockStore, loadingAll: true };
    render(<CalendarView store={loadingStore} />);

    expect(screen.getByText("Loading...")).toBeInTheDocument();
  });
});
