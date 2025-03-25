// import React from "react";
// import { screen, fireEvent, waitFor } from "@testing-library/react";
// import { ImageSection } from "../../../pages/ReportsAndManagamentPages/ImageSection";
// import userEvent from "@testing-library/user-event";
// import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
// import { renderWithProviders } from "../../../utils/utils";

// jest.mock("../../../models/useGetSiteSettings", () => () => ({
//   data: { maximumMediaSizeMegabytes: 40 },
// }));

// jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore", () => ({
//   ReportEncounterStore: jest.fn().mockImplementation(() => ({
//     isHumanLocal: true,
//     imageRequired: true,
//     imageSectionError: false,
//     imageSectionSubmissionId: "mockSubmissionId",
//     imageSectionFileNames: [],
//     imagePreview: [],
//     exifDateTime: null,
//     setImageSectionSubmissionId: jest.fn(),
//     setImageSectionFileNames: jest.fn(),
//     setImagePreview: jest.fn(),
//     setImageCount: jest.fn(),
//     setExifDateTime: jest.fn(),
//     setImageSectionError: jest.fn(),
//   })),
// }));

// const mockStore = new ReportEncounterStore();

// const renderComponent = () => {
//   return renderWithProviders(<ImageSection store={mockStore} />);
// };

// describe("ImageSection Component", () => {
//   test("renders the component correctly", () => {
//     renderComponent();
//     expect(screen.getByText("PHOTOS_SECTION")).toBeInTheDocument();
//   });

//   test("displays an alert if user is anonymous", () => {
//     mockStore.isHumanLocal = false;
//     renderComponent();
//     expect(screen.getByText("ANON_UPLOAD_IMAGE_WARNING")).toBeInTheDocument();
//   });

//   test("allows users to select files", async () => {
//     renderComponent();
//     const fileInput = screen.getByLabelText(/BROWSE/i);
//     // const fileInput = screen.getByTestId("file-input");


//     const file = new File(["dummy content"], "example.jpg", {
//       type: "image/jpeg",
//     });
//     await userEvent.upload(fileInput, file);

//     await waitFor(() =>
//       expect(mockStore.imagePreview.length).toBeGreaterThan(0),
//     );
//   });

//   test("prevents unsupported file types", async () => {
//     renderComponent();
//     const fileInput = screen.getByLabelText(/BROWSE/i);
//     const file = new File(["content"], "example.txt", { type: "text/plain" });

//     await userEvent.upload(fileInput, file);
//     await waitFor(() => expect(mockStore.imagePreview.length).toBe(0));
//   });

//   test("removes files from the preview list", async () => {
//     renderComponent();
//     const fileInput = screen.getByLabelText(/BROWSE/i);
//     const file = new File(["dummy content"], "example.jpg", {
//       type: "image/jpeg",
//     });
//     await userEvent.upload(fileInput, file);

//     await waitFor(() =>
//       expect(screen.getByText("example.jpg")).toBeInTheDocument(),
//     );
//     fireEvent.click(screen.getByRole("button", { name: /remove/i }));
//     await waitFor(() =>
//       expect(screen.queryByText("example.jpg")).not.toBeInTheDocument(),
//     );
//   });

//   test("shows file size exceeded warning", async () => {
//     renderComponent();
//     const fileInput = screen.getByLabelText(/BROWSE/i);
//     const largeFile = new File(["large file content"], "large.jpg", {
//       size: 50 * 1024 * 1024,
//       type: "image/jpeg",
//     });

//     await userEvent.upload(fileInput, largeFile);
//     await waitFor(() =>
//       expect(screen.getByText("FILE_SIZE_EXCEEDED")).toBeInTheDocument(),
//     );
//   });
// });


import React from "react";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ImageSection } from "../../../pages/ReportsAndManagamentPages/ImageSection";
import { ReportEncounterStore } from "../../../pages/ReportsAndManagamentPages/ReportEncounterStore";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../models/useGetSiteSettings", () => () => ({
  data: { maximumMediaSizeMegabytes: 40 },
}));

jest.mock("../../../pages/ReportsAndManagamentPages/ReportEncounterStore", () => ({
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
}));

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
    await waitFor(() =>
      expect(mockStore.setImagePreview).toHaveBeenCalled()
    );
  });

  // test("prevents unsupported file types", async () => {
  //   renderComponent();
  //   const fileInput = document.querySelector("input[type=file]"); 
  //   const file = new File(["content"], "example.abc", { type: "text/plain" });

  //   await userEvent.upload(fileInput, file);
  //   await waitFor(() =>
  //     expect(mockStore.setImagePreview).not.toHaveBeenCalled()
  //   );
  // });

  // test("removes files from the preview list", async () => {
  //   mockStore.imagePreview = [{ fileName: "example.jpg", src: "data:image/jpg;base64,...", fileSize: 1000, progress: 100 }];
  //   renderComponent();

  //   expect(screen.getByText("example.jpg")).toBeInTheDocument();

  //   fireEvent.click(screen.getByTestId("remove-example.jpg"));
  //   await waitFor(() =>
  //     expect(mockStore.setImageSectionFileNames).toHaveBeenCalledWith("example.jpg", "remove")
  //   );
  // });

  // test("shows file size exceeded warning", async () => {
  //   renderComponent();
  //   const fileInput = screen.getByTestId("file-input");
  //   const largeFile = new File(["x".repeat(1024 * 1024 * 50)], "large.jpg", {
  //     type: "image/jpeg",
  //   });
  //   Object.defineProperty(largeFile, 'size', { value: 50 * 1024 * 1024 });

  //   await userEvent.upload(fileInput, largeFile);
  //   await waitFor(() =>
  //     expect(screen.getByText("FILE_SIZE_EXCEEDED")).toBeInTheDocument(),
  //   );
  // });
});

