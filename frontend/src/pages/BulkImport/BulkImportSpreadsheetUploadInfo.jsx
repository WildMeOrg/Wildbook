import React from "react";
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { MdTableChart } from "react-icons/md";
import ThemeColorContext from "../../ThemeColorProvider";

export const BulkImportSpreadsheetUploadInfo = observer(({ store }) => {
  const data = [
    { label: "excel sheets in file", value: store.worksheetInfo.sheetCount },
    { label: "excel rows in file", value: store.worksheetInfo.rowCount },
    { label: "excel columns in file", value: store.worksheetInfo.columnCount },
  ];

  const theme = React.useContext(ThemeColorContext);

  return (
    <div style={{ marginLeft: "2rem", marginTop: "2rem" }}>
      <InfoAccordion
        icon={<MdTableChart size={20} color={theme.primaryColors.primary500} />}
        title="Spreadsheet Upload Info"
        data={data}
      />
    </div>
  );
});

export default BulkImportSpreadsheetUploadInfo;
