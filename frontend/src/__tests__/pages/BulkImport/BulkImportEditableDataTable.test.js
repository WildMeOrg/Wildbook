import React from "react";
import { screen, fireEvent } from "@testing-library/react";
import { DataTable } from "../../../pages/BulkImport/EditableDataTable";
import { renderWithProviders } from "../../../utils/utils";
import { useSiteSettings } from "../../../SiteSettingsContext";

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
  validateMediaAssetColumnsOnly: jest.fn(() => ({ errors: {}, warnings: {} })),
  photoColumns: ["Encounter.mediaAsset0"],
  isBoxedSheet: false,
  setValidViewpoints: jest.fn(),
  setMatchableClasses: jest.fn(),
  getOptionsForSelectCell: jest.fn(() => [
    { value: "OptionA", label: "OptionA" },
    { value: "OptionB", label: "OptionB" },
  ]),
};

jest.mock("../../../SiteSettingsContext", () => ({
  useSiteSettings: jest.fn(),
}));

useSiteSettings.mockReturnValue({
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
  isLoading: false,
  error: null,
});

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

  describe("boxed sheet", () => {
    const A0 = "Encounter.mediaAsset0";
    const A2 = "Encounter.mediaAsset2";
    const boxedStore = {
      ...mockStore,
      isBoxedSheet: true,
      columnsDef: ["name", A0, `${A0}.bbox`, `${A0}.keywords`, A2],
      photoColumns: [A0, A2],
      spreadsheetData: [
        {
          name: "Alice",
          [A0]: "a.jpg",
          [`${A0}.bbox`]: "1,2,3",
          [`${A0}.keywords`]: "kw",
          [A2]: "c.jpg",
        },
      ],
      validationErrors: {
        0: {
          [`${A0}.bbox`]: "BULKIMPORT_ERROR_INVALID_BBOX",
          [A2]: "MISSINGc.jpg",
        },
      },
      validationWarnings: {
        0: {
          name: {
            id: "BULKIMPORT_WARNING_IACLASS_NOT_MATCHABLE",
            values: { allowed: "x" },
          },
        },
      },
    };

    test("photo and per-photo cells are read-only, others editable", () => {
      renderWithProviders(<DataTable store={boxedStore} />);
      ["a.jpg", "1,2,3", "kw", "c.jpg"].forEach((v) =>
        expect(screen.getByDisplayValue(v)).toHaveAttribute("readonly"),
      );
      expect(screen.getByDisplayValue("Alice")).not.toHaveAttribute("readonly");
    });

    test("read-only cells still show their errors, on every photo slot", () => {
      renderWithProviders(<DataTable store={boxedStore} />);
      ["1,2,3", "c.jpg"].forEach((v) =>
        expect(
          screen
            .getByDisplayValue(v)
            .parentElement.querySelector(".invalid-feedback"),
        ).not.toBeNull(),
      );
    });

    test("a warning with values renders", () => {
      renderWithProviders(<DataTable store={boxedStore} />);
      const warning = screen
        .getByDisplayValue("Alice")
        .parentElement.querySelector(".text-warning");
      expect(warning.textContent).not.toBe("");
    });
  });
});
