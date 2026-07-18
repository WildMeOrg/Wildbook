import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { DateTimeSection } from "../../../pages/ReportsAndManagamentPages/DateTimeSection";
import userEvent from "@testing-library/user-event";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("react-intl", () => {
  const actual = jest.requireActual("react-intl");
  return {
    ...actual,
    FormattedMessage: ({ id }) => id,
  };
});

jest.mock("moment", () => {
  const originalMoment = jest.requireActual("moment");
  return (date, format, strict) => {
    if (strict && format === originalMoment.ISO_8601) {
      return {
        isValid: () => originalMoment(date, format, strict).isValid(),
      };
    }
    return originalMoment(date);
  };
});

jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore", () => ({
  ReportEncounterStore: jest.fn().mockImplementation(() => ({
    dateTimeSection: {
      value: "",
      required: true,
      error: false,
    },
    setDateTimeSectionValue: jest.fn(),
    setDateTimeSectionError: jest.fn(),
    exifDateTime: ["2024-03-12 15:30", "2024-03-10 12:00"],
  })),
}));

const mockStore = new ReportEncounterStore();

const renderComponent = () => {
  return renderWithProviders(<DateTimeSection store={mockStore} />);
};

describe("DateTimeSection Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getAllByText("DATETIME_SECTION")[1]).toBeInTheDocument();
  });

  test("allows users to input a valid date and time", async () => {
    renderComponent();
    const input = screen.getByPlaceholderText("YYYY-MM-DD");

    await userEvent.type(input, "2024-03-12");
    fireEvent.blur(input);

    await waitFor(() =>
      expect(mockStore.setDateTimeSectionError).toHaveBeenCalledWith(false),
    );
  });

  test("shows error on invalid date format", async () => {
    mockStore.dateTimeSection = {
      value: "2025-03-03 0",
      required: true,
      error: true,
      setDateTimeSectionValue: jest.fn(),
      setDateTimeSectionError: jest.fn(),
    };
    renderComponent();
    const input = screen.getByPlaceholderText("YYYY-MM-DD");

    fireEvent.change(input, { target: { value: "2025-03-03 0" } });
    fireEvent.blur(input);

    await waitFor(() => {
      expect(mockStore.setDateTimeSectionError).toHaveBeenCalledWith(true);
      expect(mockStore.dateTimeSection.error).toBe(true);
      expect(screen.getByText("INVALID_DATETIME_WARNING")).toBeInTheDocument();
    });
  });

  test("allows selecting EXIF date", async () => {
    renderComponent();
    const select = screen.getByLabelText("DATETIME_EXIF");
    fireEvent.change(select, { target: { value: "2024-03-12 15:30" } });

    await waitFor(() =>
      expect(mockStore.setDateTimeSectionValue).toHaveBeenCalledWith(
        expect.objectContaining({
          format: expect.any(Function),
        }),
      ),
    );
    const selectedValue = mockStore.setDateTimeSectionValue.mock.calls.at(-1)[0];
    expect(selectedValue.format("YYYY-MM-DDTHH:mm")).toBe("2024-03-12T15:30");
  });
});
