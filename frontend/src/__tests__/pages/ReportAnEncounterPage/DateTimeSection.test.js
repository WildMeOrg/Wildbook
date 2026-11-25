import React from "react";
import { screen, fireEvent, act } from "@testing-library/react";
import { DateTimeSection } from "../../../pages/ReportsAndManagamentPages/DateTimeSection";
import userEvent from "@testing-library/user-event";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
import { renderWithProviders } from "../../../utils/utils";

let store;

const renderComponent = () => {
  store = new ReportEncounterStore();
  store.setExifDateTime("2024-03-12 15:30");
  store.setExifDateTime("2024-03-10 12:00");
  renderWithProviders(<DateTimeSection store={store} />);
  return screen.getByPlaceholderText("YYYY-MM-DD");
};

describe("DateTimeSection Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getAllByText("DATETIME_SECTION")[1]).toBeInTheDocument();
  });

  test("accepts a typed full date", async () => {
    const input = renderComponent();

    await userEvent.type(input, "2024-03-12");
    fireEvent.blur(input);

    expect(input).toHaveValue("2024-03-12");
    expect(store.dateTimeSection.value).toBe("2024-03-12");
    expect(store.dateTimeSection.error).toBe(false);
    expect(screen.queryByText("INVALID_DATETIME_WARNING")).toBeNull();
  });

  test("shows error on invalid date format", async () => {
    const input = renderComponent();

    await userEvent.type(input, "2025-03-03 0");
    fireEvent.blur(input);

    expect(store.dateTimeSection.error).toBe(true);
    expect(screen.getByText("INVALID_DATETIME_WARNING")).toBeInTheDocument();
  });

  test("allows selecting EXIF date", () => {
    renderComponent();
    fireEvent.change(screen.getByLabelText("DATETIME_EXIF"), {
      target: { value: "2024-03-12 15:30" },
    });

    expect(store.dateTimeSection.value.format("YYYY-MM-DDTHH:mm")).toBe(
      "2024-03-12T15:30",
    );
  });

  test("selecting the empty EXIF option clears the field", async () => {
    const input = renderComponent();
    await userEvent.type(input, "2024-03");
    const select = screen.getByLabelText("DATETIME_EXIF");

    fireEvent.change(select, { target: { value: "2024-03-12 15:30" } });
    fireEvent.change(select, { target: { value: "" } });

    expect(store.dateTimeSection.value).toBe("");
    expect(input).toHaveValue("");
  });
});

describe("DateTimeSection partial dates", () => {
  test("typing a year-month keeps the exact text and shows no warning on blur", async () => {
    const input = renderComponent();

    await userEvent.type(input, "2024-03");
    fireEvent.blur(input);

    expect(input).toHaveValue("2024-03");
    expect(store.dateTimeSection.value).toBe("2024-03");
    expect(store.dateTimeSection.error).toBe(false);
    expect(screen.queryByText("INVALID_DATETIME_WARNING")).toBeNull();
  });

  test("a malformed month shows the warning on blur", async () => {
    const input = renderComponent();

    await userEvent.type(input, "2024-3");
    fireEvent.blur(input);

    expect(store.dateTimeSection.error).toBe(true);
    expect(screen.getByText("INVALID_DATETIME_WARNING")).toBeInTheDocument();
  });

  test("editing the middle of a partial date keeps the caret in place", async () => {
    const input = renderComponent();
    await userEvent.type(input, "2024-03");

    // replace the "4" of 2024 with "3"
    await userEvent.type(input, "3", {
      initialSelectionStart: 3,
      initialSelectionEnd: 4,
    });

    expect(input).toHaveValue("2023-03");
    expect(input.selectionStart).toBe(4);
    expect(store.dateTimeSection.value).toBe("2023-03");
  });

  test("a partial date set after mount (login restore) is displayed", () => {
    const input = renderComponent();

    act(() => store.setDateTimeSectionValue("2024-03"));

    expect(input).toHaveValue("2024-03");
  });
});
