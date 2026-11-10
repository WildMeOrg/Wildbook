import React from "react";
import { render, fireEvent, screen } from "@testing-library/react";
import SelectCell from "../../components/SelectCell";

jest.mock("react-select", () => {
  const MockSelect = ({ onChange }) => (
    <button onClick={() => onChange({ value: "Giraffa giraffa" })}>pick</button>
  );
  MockSelect.displayName = "Select";
  return MockSelect;
});

const storeMock = () => ({
  getOptionsForSelectCell: jest.fn(() => []),
  spreadsheetData: [{}],
  updateCellValue: jest.fn(),
  revalidate: jest.fn(),
  validateRow: jest.fn(() => ({ errors: {}, warnings: {} })),
  mergeValidationError: jest.fn(),
  mergeValidationWarning: jest.fn(),
  setApplyToAllRowModalShow: jest.fn(),
});

const pick = (store, columnId) => {
  render(
    <SelectCell
      store={store}
      columnId={columnId}
      rowIndex={0}
      setColId={jest.fn()}
      setColValue={jest.fn()}
    />,
  );
  fireEvent.click(screen.getByText("pick"));
};

describe("SelectCell", () => {
  test("a species change revalidates the whole sheet", () => {
    const store = storeMock();
    pick(store, "Encounter.genus");
    expect(store.revalidate).toHaveBeenCalledTimes(1);
    expect(store.validateRow).not.toHaveBeenCalled();
  });

  test("other columns still validate only their row", () => {
    const store = storeMock();
    pick(store, "Encounter.sex");
    expect(store.revalidate).not.toHaveBeenCalled();
    expect(store.validateRow).toHaveBeenCalledWith(0);
  });
});
