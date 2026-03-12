import React from "react";
import { render, fireEvent, act } from "@testing-library/react";
import "@testing-library/jest-dom";

jest.mock("../../../models/useGetSiteSettings", () => ({
  __esModule: true,
  default: () => ({ data: { maximumMediaSizeMegabytes: 5 } }),
}));
jest.mock("../../../pages/BulkImport/BulkImportImageUploadInfo", () => ({
  __esModule: true,
  BulkImportImageUploadInfo: ({ expanded }) => (
    <div>ImageUploadInfo-{expanded}</div>
  ),
}));
jest.mock("../../../pages/BulkImport/BulkImportSeeInstructionsButton", () => ({
  __esModule: true,
  default: () => <button>Instructions</button>,
}));
jest.mock("react-window", () => ({
  FixedSizeList: ({ itemData, children }) => (
    <div data-testid="image-preview-list">
      {itemData.map((_, index) => (
        <div key={index}>{children({ index, style: {} })}</div>
      ))}
    </div>
  ),
}));
jest.mock("react-intl", () => ({
  FormattedMessage: ({ id, defaultMessage, values }) => (
    <span>
      {defaultMessage || id}
      {values && values.maxSize}
    </span>
  ),
}));

import { BulkImportImageUpload } from "../../../pages/BulkImport/BulkImportImageUpload";
import ThemeContext from "../../../ThemeColorProvider";

describe("BulkImportImageUpload", () => {
  let store;
  let utils;
  const theme = {
    primaryColors: {
      primary500: "#007BFF",
      primary50: "#E0F3FF",
      primary700: "#0056b3",
    },
    wildMeColors: { cyan700: "#17a2b8" },
    defaultColors: { white: "#fff" },
    statusColors: { red500: "#dc3545", red800: "#721c24" },
  };

  beforeEach(() => {
    store = {
      filesParsed: false,
      imagePreview: [],
      maxImageCount: 10,
      setMaxImageCount: jest.fn(),
      setMaxImageSizeMB: jest.fn(),
      initializeFlow: jest.fn(() => {}),
      triggerUploadAfterFileInput: jest.fn(),
      generateThumbnailsForFirst50: jest.fn(),
      setFilesParsed: jest.fn(),
      setImageSectionFileNames: jest.fn(),
      uploadFilteredFiles: jest.fn(),
      traverseFileTree: jest.fn(),
      removePreview: jest.fn(),
      setActiveStep: jest.fn(),
      imageSectionError: false,
      imageRequired: false,
      handleLoginRedirect: jest.fn(),
      flow: {
        assignBrowse: jest.fn(),
        addFile: jest.fn(),
      },
    };

    utils = render(
      <ThemeContext.Provider value={theme}>
        <BulkImportImageUpload store={store} />
      </ThemeContext.Provider>,
    );
  });

  test("sets max image count on render", () => {
    expect(store.setMaxImageCount).toHaveBeenCalledTimes(1);
    expect(store.setMaxImageCount).toHaveBeenCalledWith(200);
  });

  test("assigns browse handler to file input", () => {
    expect(store.flow.assignBrowse).toHaveBeenCalledTimes(1);
  });

  test("triggers upload after file input change", () => {
    const input = utils.container.querySelector('input[type="file"]');
    fireEvent.change(input);
    expect(store.triggerUploadAfterFileInput).toHaveBeenCalled();
  });

  test("navigates to next step on NEXT click", () => {
    const nextBtn = utils.getByText(/NEXT/);
    fireEvent.click(nextBtn);
    expect(store.setActiveStep).toHaveBeenCalledWith(1);
  });

  test("renders instructions and browse UI", () => {
    expect(utils.getByText(/ImageUploadInfo/)).toBeInTheDocument();
    expect(utils.getByText("Instructions")).toBeInTheDocument();
    expect(utils.getByText(/BROWSE/)).toBeInTheDocument();
  });

  test("handles drag over event", () => {
    const dropArea = utils.container.querySelector("#drop-area");
    const dataTransfer = { dropEffect: "" };
    fireEvent.dragOver(dropArea, { dataTransfer });
    expect(dataTransfer.dropEffect).toBe("copy");
    const event = {
      preventDefault: jest.fn(),
      dataTransfer: { dropEffect: "" },
    };
    fireEvent.dragOver(dropArea, event);
    expect(event.dataTransfer.dropEffect).toBe("copy");
  });

  test("drops valid image files correctly", () => {
    const dropArea = utils.container.querySelector("#drop-area");
    const file = new File(["x"], "img.png", { type: "image/png" });
    const dataTransfer = {
      items: [{ getAsFile: () => file }],
      types: ["Files"],
      dropEffect: "",
    };
    fireEvent.drop(dropArea, { dataTransfer });
    expect(store.flow.addFile).toHaveBeenCalledWith(file);
    expect(store.uploadFilteredFiles).toHaveBeenCalled();
  });

  test("does not add non-image files", () => {
    const dropArea = utils.container.querySelector("#drop-area");
    const file = new File(["x"], "file.txt", { type: "text/plain" });
    const dataTransfer = {
      items: [{ getAsFile: () => file }],
      types: ["Files"],
    };
    fireEvent.drop(dropArea, { dataTransfer });
    expect(store.flow.addFile).not.toHaveBeenCalled();
  });

  test("uses traverseFileTree for directory entries", () => {
    const dropArea = utils.container.querySelector("#drop-area");
    const entry = { isFile: true };
    const item = { webkitGetAsEntry: () => entry };
    const dataTransfer = { items: [item], types: ["Files"] };
    fireEvent.drop(dropArea, { dataTransfer });
    expect(store.traverseFileTree).toHaveBeenCalledWith(entry);
  });

  test("renders image error alert when imageSectionError is true", () => {
    store.imageSectionError = true;
    const { getByText } = render(
      <ThemeContext.Provider value={theme}>
        <BulkImportImageUpload store={store} />
      </ThemeContext.Provider>,
    );
    expect(getByText(/IMAGES_REQUIRED_ANON_WARNING/)).toBeInTheDocument();
  });

  test("removePreview called on delete icon click", () => {
    store.imagePreview = [
      {
        fileName: "f.jpg",
        src: null,
        showThumbnail: false,
        progress: 0,
        fileSize: 1,
        error: false,
      },
    ];
    act(() => {
      store.filesParsed = true;
    });
    const { getByTitle } = render(
      <ThemeContext.Provider value={theme}>
        <BulkImportImageUpload store={store} />
      </ThemeContext.Provider>,
    );
    fireEvent.click(getByTitle("Remove image"));
    expect(store.removePreview).toHaveBeenCalledWith("f.jpg");
  });
});
