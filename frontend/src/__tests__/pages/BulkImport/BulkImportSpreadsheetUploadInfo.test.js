import React from "react";
import { render, screen } from "@testing-library/react";
import { BulkImportSpreadsheetUploadInfo } from "../../../pages/BulkImport/BulkImportSpreadsheetUploadInfo";
import ThemeColorContext from "../../../ThemeColorProvider";

jest.mock("react-intl", () => ({
  useIntl: () => ({
    formatMessage: ({ defaultMessage }, values) =>
      values
        ? defaultMessage.replace("{sheetCount}", values.sheetCount)
        : defaultMessage,
  }),
}));

jest.mock("react-icons/md", () => ({
  MdTableChart: (props) => <svg data-testid="md-table-chart" {...props} />,
}));

jest.mock("../../../components/InfoAccordion", () => {
  const component = (props) => (
    <div
      data-testid="info-accordion"
      data-title={props.title}
      data-data={JSON.stringify(props.data)}
    >
      {props.icon}
    </div>
  );
  component.displayName = "InfoAccordion";
  return component;
});

describe("BulkImportSpreadsheetUploadInfo", () => {
  const store = {
    worksheetInfo: {
      sheetCount: 5,
      rowCount: 100,
      columnCount: 10,
    },
  };

  const theme = {
    primaryColors: {
      primary500: "#123abc",
    },
  };

  beforeEach(() => {
    render(
      <ThemeColorContext.Provider value={theme}>
        <BulkImportSpreadsheetUploadInfo store={store} />
      </ThemeColorContext.Provider>,
    );
  });

  it("renders an InfoAccordion with the correct title", () => {
    const accordion = screen.getByTestId("info-accordion");
    expect(accordion).toHaveAttribute("data-title", "Excel Sheet Info: 5");
  });

  it("passes the correct data array to InfoAccordion", () => {
    const accordion = screen.getByTestId("info-accordion");
    const data = JSON.parse(accordion.getAttribute("data-data"));
    expect(data).toEqual([
      { label: "excel sheets in file", value: 5 },
      { label: "excel rows in file", value: 100 },
      { label: "excel columns in file", value: 10 },
    ]);
  });

  it("renders the MdTableChart icon with the theme color", () => {
    const icon = screen.getByTestId("md-table-chart");
    expect(icon).toHaveAttribute("color", theme.primaryColors.primary500);
    expect(icon).toBeInTheDocument();
  });
});
