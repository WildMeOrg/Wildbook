import React from "react";
import { screen } from "@testing-library/react";
import ReportConfirm from "../../../pages/ReportsAndManagamentPages/ReportConfirm";
import { renderWithProviders } from "../../../utils/utils";
import userEvent from "@testing-library/user-event";

jest.mock("react-router-dom", () => ({
  ...jest.requireActual("react-router-dom"),
  useLocation: () => ({
    state: { responseData },
  }),
}));

const responseData = {
  id: "12345",
  locationId: "67890",
  submissionDate: "2025-03-19",
  assets: [
    { id: "1", url: "https://example.com/image1.jpg", filename: "image1.jpg" },
    { id: "2", url: "https://example.com/image2.jpg", filename: "image2.jpg" },
  ],
  invalidFiles: [{ filename: "invalid1.jpg" }, { filename: "invalid2.jpg" }],
};

describe("ReportConfirm Component", () => {
  test("renders submission success message", () => {
    renderWithProviders(<ReportConfirm />);
    expect(screen.getByText(/SUBMISSION_SUCCESSFUL/i)).toBeInTheDocument();
    expect(screen.getByText(/SUBMISSION_SUCCESS_ALERT/i)).toBeInTheDocument();
  });

  test("renders encounter details", () => {
    renderWithProviders(<ReportConfirm />);
    expect(screen.getByText(/VIEW_ENCOUNTER/i)).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(`ENCOUNTER\\s*${responseData.id}`, "i")),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        new RegExp(
          `SUBMISSION_SUCCESS_LOCATION_ID\\s*${responseData.locationId}`,
          "i",
        ),
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        new RegExp(
          `SUBMISSION_SUCCESS_SUBMITTED_ON\\s*${responseData.submissionDate}`,
          "i",
        ),
      ),
    ).toBeInTheDocument();
  });

  test("renders images correctly", () => {
    renderWithProviders(<ReportConfirm />);
    const images = screen.getAllByRole("img");
    expect(images).toHaveLength(responseData.assets.length);
    expect(images[0]).toHaveAttribute("src", responseData.assets[0].url);
    expect(images[1]).toHaveAttribute("src", responseData.assets[1].url);
  });

  test("renders invalid file warnings when present", () => {
    renderWithProviders(<ReportConfirm />);
    expect(screen.getByText(/INVALID_FILES_MESSAGE/i)).toBeInTheDocument();
    expect(screen.getByText("invalid1.jpg")).toBeInTheDocument();
    expect(screen.getByText("invalid2.jpg")).toBeInTheDocument();
  });

  test("renders contact message link", () => {
    renderWithProviders(<ReportConfirm />);
    const contactLink = screen.getByText(/community.wildme.org/i);
    expect(contactLink).toBeInTheDocument();
    expect(contactLink).toHaveAttribute("href", "https://community.wildme.org");
  });

  test("handles view encounter button click", async () => {
    renderWithProviders(<ReportConfirm />);
    global.open = jest.fn();
    const button = screen.getByText(/VIEW_ENCOUNTER/i);
    await userEvent.click(button);
    expect(global.open).toHaveBeenCalledWith(
      `/encounters/encounter.jsp?number=${responseData.id}`,
      "_blank",
    );
  });

  test("handles submit next encounter button click", async () => {
    renderWithProviders(<ReportConfirm />);
    delete window.location;
    window.location = { href: "" };
    const button = screen.getByText(/SUBMIT_NEXT_ENCOUNTER/i);
    await userEvent.click(button);
    expect(window.location.href).toBe("/react/report");
  });
});
