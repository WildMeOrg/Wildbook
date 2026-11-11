import React from "react";
import { FormattedMessage } from "react-intl";
import { BulkImportSetLocation } from "../../../pages/BulkImport/BulkImportSetLocation";
import BulkImportStore from "../../../pages/BulkImport/BulkImportStore";
import { renderWithProviders } from "../../../utils/utils";

jest.mock("@flowjs/flow.js", () =>
  jest.fn().mockImplementation(() => ({
    opts: { query: {} },
    assignBrowse: jest.fn(),
    on: jest.fn(),
    files: [],
  })),
);
jest.mock("../../../models/bulkImport/usePostBulkImport", () => () => ({
  submit: jest.fn(),
  isLoading: false,
}));

jest.mock("antd/es/tree-select", () => {
  const TreeSelect = () => <div data-testid="tree-select" />;
  TreeSelect.displayName = "TreeSelect";
  return TreeSelect;
});

const A0 = "Encounter.mediaAsset0";
const A2 = "Encounter.mediaAsset2";

const realStore = (columns, rows) => {
  const store = new BulkImportStore();
  store.setIndexableFields(["Encounter.mediaAsset#.bbox"]);
  store.setColumnsDef(columns);
  store.setSpreadsheetData(rows);
  return store;
};

describe("un-boxed photo warning on the last step", () => {
  beforeEach(() => FormattedMessage.mockClear());

  test("counts photos without a box from the sheet itself", () => {
    const store = realStore(
      [A0, `${A0}.bbox`, A2, `${A2}.bbox`],
      [
        { [A0]: "a.jpg", [`${A0}.bbox`]: "1,1,5,5", [A2]: "c.jpg" },
        { [A0]: "d.jpg", [A2]: "e.jpg" },
      ],
    );
    renderWithProviders(<BulkImportSetLocation store={store} />);
    expect(document.getElementById("unboxed-photos-warning")).not.toBeNull();
    expect(FormattedMessage).toHaveBeenCalledWith(
      { id: "BULKIMPORT_UNBOXED_PHOTOS_WARNING", values: { count: 3 } },
      expect.anything(),
    );
  });

  test("no warning when the box column is empty everywhere", () => {
    const store = realStore(
      [A0, `${A0}.bbox`],
      [{ [A0]: "a.jpg", [`${A0}.bbox`]: "" }],
    );
    renderWithProviders(<BulkImportSetLocation store={store} />);
    expect(document.getElementById("unboxed-photos-warning")).toBeNull();
  });
});
