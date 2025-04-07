import React from "react";
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ImageSection } from "../../../pages/ReportsAndManagamentPages/ImageSection";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../models/useGetSiteSettings", () => () => ({
  data: { maximumMediaSizeMegabytes: 40 },
}));

jest.mock(
  "../../../pages/ReportsAndManagamentPages/ReportEncounterStore",
  () => ({
    ReportEncounterStore: jest.fn().mockImplementation(() => ({
      isHumanLocal: true,
      imageRequired: false,
      imageSectionError: false,
      imageSectionSubmissionId: "mockSubmissionId",
      imageSectionFileNames: [],
      imagePreview: [],
      exifDateTime: null,
      setImageSectionSubmissionId: jest.fn(),
      setImageSectionFileNames: jest.fn(),
      setImagePreview: jest.fn(),
      setImageCount: jest.fn(),
      setExifDateTime: jest.fn(),
      setImageSectionError: jest.fn(),
    })),
  }),
);

const mockStore = new ReportEncounterStore();

const renderComponent = () => {
  return renderWithProviders(<ImageSection store={mockStore} />);
};

describe("ImageSection Component", () => {
  test("renders the component correctly", () => {
    renderComponent();
    expect(screen.getByText(/PHOTOS_SECTION/i)).toBeInTheDocument();
  });

  test("image section is required", () => {
    mockStore.imageRequired = true;
    renderComponent();
    expect(screen.getByText(/PHOTOS_SECTION\s*\*/i)).toBeInTheDocument();
  });

  test("displays alert if user is anonymous", () => {
    mockStore.isHumanLocal = false;
    renderComponent();
    expect(screen.getByText("ANON_UPLOAD_IMAGE_WARNING")).toBeInTheDocument();
  });

  test("allows users to select files", async () => {
    renderComponent();
    const fileInput = document.querySelector("input[type=file]");
    expect(fileInput).toBeInTheDocument();
    const file = new File(["dummy content"], "example.jpg", {
      type: "image/jpeg",
    });
    await userEvent.upload(fileInput, file);
    await waitFor(() => expect(mockStore.setImagePreview).toHaveBeenCalled());
  });
});
