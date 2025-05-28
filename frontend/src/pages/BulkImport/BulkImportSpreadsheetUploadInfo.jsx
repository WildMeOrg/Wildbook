import React from "react";
import { observer } from "mobx-react-lite";
import InfoAccordion from "../../components/InfoAccordion";
import { MdTableChart } from "react-icons/md";

export const BulkImportSpreadsheetUploadInfo = observer(({ store }) => {
  console.log("store.worksheetInfo", JSON.stringify(store.worksheetInfo));
  const data = [
    { label: "excel sheets in file", value: store.worksheetInfo.sheetCount },
    { label: "excel rows in file", value: store.worksheetInfo.rowCount },
    { label: "excel columns in file", value: store.worksheetInfo.columnCount },
  ];

  return (
    <div style={{ marginLeft: "2rem", marginTop: "2rem" }}>
      <InfoAccordion
        icon={<MdTableChart size={20} color={"#00b3d9"} />}
        title="Spreadsheet Upload Info"
        data={data}
      />
    </div>
  );
});

export default BulkImportSpreadsheetUploadInfo;
