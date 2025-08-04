import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { DataTable } from "../../../pages/BulkImport/EditableDataTable";
import { renderWithProviders } from "../../../utils/utils";

const mockStore = {
  spreadsheetData: [
    { name: "Alice", age: "30" },
    { name: "Bob", age: "25" },
  ],
  columnsDef: ["name", "age"],
  validationErrors: {},
  validationWarnings: {},
  updateCellValue: jest.fn(),
  invalidateValidation: jest.fn(),
  validateSpreadsheet: jest.fn(() => ({ errors: {}, warnings: {} })),
  setValidationErrors: jest.fn(),
  setValidationWarnings: jest.fn(),
  convertToTreeData: jest.fn((data) => data),
  uploadedImages: [],
  errorPages: new Set(),
  setMinimalFields: jest.fn(),
  setValidLocationIDs: jest.fn(),
  setLocationIDOptions: jest.fn(),
  setValidSubmitterIDs: jest.fn(),
  setValidSpecies: jest.fn(),
  setValidCountryIDs: jest.fn(),
  setValidSex: jest.fn(),
  setValidLifeStages: jest.fn(),
  setValidLivingStatus: jest.fn(),
  setValidBehavior: jest.fn(),
  setValidStates: jest.fn(),
  setSynonymFields: jest.fn(),
  setLabeledKeywordAllowedKeys: jest.fn(),
  setLabeledKeywordAllowedPairs: jest.fn(),
  mergeValidationError: jest.fn(),
  mergeValidationWarning: jest.fn(),
  validateMediaAsset0ColumnOnly: jest.fn(() => ({ errors: {}, warnings: {} })),
  getOptionsForSelectCell: jest.fn(() => [
    { value: "OptionA", label: "OptionA" },
    { value: "OptionB", label: "OptionB" },
  ]),
};

jest.mock("../../../models/useGetSiteSettings", () => () => ({
  data: {
    bulkImportMinimalFields: {},
    locationData: { locationID: ["loc1", "loc2"] },
    users: [{ username: "user1" }],
    siteTaxonomies: [{ scientificName: "Panthera leo" }],
    country: ["US", "CA"],
    sex: ["male", "female"],
    lifeStage: ["adult", "juvenile"],
    livingStatus: ["alive", "dead"],
    behavior: ["walking", "resting"],
    labeledKeywordAllowedValues: {
      key1: ["val1", "val2"],
    },
  },
}));

describe("EditableDataTable", () => {
  test("renders the table with correct headers", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    expect(screen.getByText("#")).toBeInTheDocument();
    expect(screen.getByText("name")).toBeInTheDocument();
    expect(screen.getByText("age")).toBeInTheDocument();
  });

  test("renders table rows and values", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    expect(screen.getByDisplayValue("Alice")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Bob")).toBeInTheDocument();
  });

  test("input value change calls store.updateCellValue", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    const input = screen.getByDisplayValue("Alice");
    fireEvent.change(input, { target: { value: "Alicia" } });
    expect(mockStore.updateCellValue).toHaveBeenCalledWith(0, "name", "Alicia");
  });

  test("on blur triggers validation logic", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    const input = screen.getByDisplayValue("Bob");
    fireEvent.blur(input);
    expect(mockStore.invalidateValidation).toHaveBeenCalled();
    expect(mockStore.validateSpreadsheet).toHaveBeenCalled();
    expect(mockStore.setValidationErrors).toHaveBeenCalled();
    expect(mockStore.setValidationWarnings).toHaveBeenCalled();
  });

  test("shows row number as first column", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    const rows = screen.getAllByText("1");
    const hasRowNumber = rows.some((el) =>
      el.classList.contains("text-center"),
    );
    expect(hasRowNumber).toBe(true);
  });

  test("renders pagination controls", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    expect(screen.getByText("PREV")).toBeInTheDocument();
    expect(screen.getByText("NEXT")).toBeInTheDocument();
  });

  test("pagination buttons are clickable", () => {
    renderWithProviders(<DataTable store={mockStore} />);
    const nextBtn = screen.getByText("NEXT");
    fireEvent.click(nextBtn);
    expect(nextBtn).toBeEnabled();
  });

  test("applies .invalid-feedback when cell has error", () => {
    const errorStore = {
      ...mockStore,
      validationErrors: { 0: { name: "Required field" } },
      errorPages: new Set(["name"]),
    };
    renderWithProviders(<DataTable store={errorStore} />);
    expect(screen.getByText("Required field")).toBeInTheDocument();
  });

  test("displays warning if present in cell", () => {
    const warnStore = {
      ...mockStore,
      validationWarnings: { 1: { age: "Suspicious age" } },
    };
    renderWithProviders(<DataTable store={warnStore} />);
    expect(screen.getByText("Suspicious age")).toBeInTheDocument();
  });

  test("does not crash if spreadsheetData is empty", () => {
    const emptyStore = {
      ...mockStore,
      spreadsheetData: [],
      columnsDef: ["name"],
      setSynonymFields: jest.fn(),
    };
    renderWithProviders(<DataTable store={emptyStore} />);
    expect(screen.getByText("#")).toBeInTheDocument();
  });
});
