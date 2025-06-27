import React from "react";
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { MdTableChart } from "react-icons/md";
import ThemeColorContext from "../../ThemeColorProvider";
import { useIntl } from "react-intl";

export const BulkImportSpreadsheetUploadInfo = observer(({ store }) => {
  const intl = useIntl();
  const data = [
    {
      label: intl.formatMessage({
        id: "EXCEL_SHEETS_IN_FILE",
        defaultMessage: "excel sheets in file",
      }),
      value: store.worksheetInfo.sheetCount,
    },
    {
      label: intl.formatMessage({
        id: "EXCEL_ROWS_IN_FILE",
        defaultMessage: "excel rows in file",
      }),
      value: store.worksheetInfo.rowCount,
    },
    {
      label: intl.formatMessage({
        id: "EXCEL_COLUMNS_IN_FILE",
        defaultMessage: "excel columns in file",
      }),
      value: store.worksheetInfo.columnCount,
    },
  ];
  const theme = React.useContext(ThemeColorContext);

  const title = intl.formatMessage(
    { id: "EXCEL_SHEET_INFO_TITLE", defaultMessage: "Excel Sheet Info: {sheetCount}" },
    { sheetCount: store.worksheetInfo.sheetCount }
  );

  return (
    <div style={{ marginLeft: "2rem", marginTop: "2rem" }}>
      <InfoAccordion
        icon={<MdTableChart size={20} color={theme.primaryColors.primary500} />}
        title={title}
        data={data}
      />
    </div>
  );
});

export default BulkImportSpreadsheetUploadInfo;
