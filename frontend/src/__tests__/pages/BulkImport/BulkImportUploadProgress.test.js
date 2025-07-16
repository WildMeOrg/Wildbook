import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { BulkImportUploadProgress } from "../../../pages/BulkImport/BulkImportUploadProgress";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("../../../components/FinishedIcon", () => ({
  FinishedIcon: () => <div data-testid="finished-icon">✔</div>,
}));

jest.mock("../../../ThemeColorProvider", () => {
  const React = require("react");
  const mockTheme = {
    primaryColors: {
      primary500: "#00aacc",
      primary100: "#d0f0ff",
      primary50: "#e8f8ff",
    },
  };

  return {
    __esModule: true,
    default: React.createContext(mockTheme),
  };
});

const createMockStore = (overrides = {}) => ({
  activeStep: 0,
  imageUploadProgress: 50,
  spreadsheetUploadProgress: 50,
  uploadedImages: [],
  validationErrors: {},
  missingRequiredColumns: [],
  setActiveStep: jest.fn(),
  ...overrides,
});

describe("BulkImportUploadProgress", () => {
  test("renders image upload progress ring", () => {
    const store = createMockStore({ imageUploadProgress: 40 });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    expect(screen.getByRole("img", { hidden: true })).toBeInTheDocument(); // FaImage inside SVG
  });

  test("renders spreadsheet upload progress ring", () => {
    const store = createMockStore({ spreadsheetUploadProgress: 40 });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const svg = screen
      .getAllByRole("img", { hidden: true })
      .find((icon) => icon.parentElement.innerHTML.includes("svg"));
    expect(svg).toBeInTheDocument();
  });

  test("shows FinishedIcon when image upload is 100% and has images", () => {
    const store = createMockStore({
      imageUploadProgress: 100,
      uploadedImages: [{ name: "img1.jpg" }],
    });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    expect(screen.getByTestId("finished-icon")).toBeInTheDocument();
  });

  test("shows FinishedIcon when spreadsheet upload is 100%", () => {
    const store = createMockStore({
      spreadsheetUploadProgress: 100,
      imageUploadProgress: 50,
    });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const allIcons = screen.getAllByTestId("finished-icon");
    const spreadsheetIcon = allIcons[1];

    expect(spreadsheetIcon).toBeInTheDocument();
  });

  test("clicking image step triggers setActiveStep(0)", () => {
    const store = createMockStore();
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const imageCircle = screen.getByTestId("step-image");
    fireEvent.click(imageCircle);
    expect(store.setActiveStep).toHaveBeenCalledWith(0);
  });

  test("clicking spreadsheet step triggers setActiveStep(1)", () => {
    const store = createMockStore();
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const svgStep = screen.getByTestId("step-spreadsheet");
    fireEvent.click(svgStep);
    expect(store.setActiveStep).toHaveBeenCalledWith(1);
  });

  test("does not render FinishedIcon on step 3 if validationErrors exist", () => {
    const store = createMockStore({
      spreadsheetUploadProgress: 100,
      validationErrors: { 0: { "Encounter.year": "Invalid" } },
      missingRequiredColumns: [],
    });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    expect(screen.getAllByTestId("finished-icon").length).toBeLessThan(3);
  });

  test("does not render FinishedIcon on step 3 if missingRequiredColumns exist", () => {
    const store = createMockStore({
      spreadsheetUploadProgress: 100,
      validationErrors: {},
      missingRequiredColumns: ["Encounter.year"],
    });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    expect(screen.getAllByTestId("finished-icon").length).toBeLessThan(3);
  });

  test("step 4 is not clickable if spreadsheetUploadProgress !== 100", () => {
    const store = createMockStore({ spreadsheetUploadProgress: 50 });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const crosshair = screen.getByText((_, node) =>
      node?.classList.contains("bi-crosshair"),
    );
    fireEvent.click(crosshair);
    expect(store.setActiveStep).not.toHaveBeenCalledWith(3);
  });

  test("step 3 is not clickable if spreadsheetUploadProgress !== 100", () => {
    const store = createMockStore({ spreadsheetUploadProgress: 50 });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const eye = screen.getByText((_, node) =>
      node?.classList.contains("bi-eye"),
    );
    fireEvent.click(eye);
    expect(store.setActiveStep).not.toHaveBeenCalledWith(2);
  });

  test("clicking step 4 triggers setActiveStep(3) if spreadsheetUploadProgress === 100", () => {
    const store = createMockStore({ spreadsheetUploadProgress: 100 });
    renderWithProviders(<BulkImportUploadProgress store={store} />);
    const crosshair = screen.getByText((_, node) =>
      node?.classList.contains("bi-crosshair"),
    );
    fireEvent.click(crosshair);
    expect(store.setActiveStep).toHaveBeenCalledWith(3);
  });

  test("step 3 has white text color when activeStep === 2", () => {
    const store = createMockStore({ activeStep: 2 });
    const { container } = renderWithProviders(
      <BulkImportUploadProgress store={store} />,
    );
    const step = container.querySelector(".bi-eye").parentElement;
    expect(step).toHaveStyle("color: #fff");
  });
});
