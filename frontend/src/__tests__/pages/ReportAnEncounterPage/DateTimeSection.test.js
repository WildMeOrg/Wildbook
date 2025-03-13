import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { DateTimeSection } from "../DateTimeSection";
import { Provider } from "mobx-react";
import userEvent from "@testing-library/user-event";
import ThemeContext from "../../ThemeColorProvider";
import { IntlProvider } from "react-intl";
import Store from "../../store";

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

const mockStore = new Store();
mockStore.dateTimeSection = {
  value: "",
  required: true,
  error: false,
  setDateTimeSectionValue: jest.fn(),
  setDateTimeSectionError: jest.fn(),
};
mockStore.exifDateTime = ["2024-03-12 15:30", "2024-03-10 12:00"];

const renderComponent = () => {
  return render(
    <Provider store={mockStore}>
      <IntlProvider locale="en" messages={{}}>
        <ThemeContext.Provider
          value={{
            statusColors: { red800: "red" },
          }}
        >
          <DateTimeSection store={mockStore} />
        </ThemeContext.Provider>
      </IntlProvider>
    </Provider>,
  );
};

describe("DateTimeSection Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByText("DATETIME_SECTION")).toBeInTheDocument();
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
    renderComponent();
    const input = screen.getByPlaceholderText("YYYY-MM-DD");

    await userEvent.type(input, "invalid-date");
    fireEvent.blur(input);

    await waitFor(() =>
      expect(mockStore.setDateTimeSectionError).toHaveBeenCalledWith(true),
    );
    expect(screen.getByText("INVALID_DATETIME_WARNING")).toBeInTheDocument();
  });

  test("allows selecting EXIF date", async () => {
    renderComponent();
    const select = screen.getByLabelText("DATETIME_EXIF");
    fireEvent.change(select, { target: { value: "2024-03-12 15:30" } });

    await waitFor(() =>
      expect(mockStore.setDateTimeSectionValue).toHaveBeenCalledWith(
        new Date("2024-03-12 15:30"),
      ),
    );
  });
});
