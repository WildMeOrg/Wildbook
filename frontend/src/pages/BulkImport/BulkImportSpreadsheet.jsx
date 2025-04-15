import React from "react";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import * as XLSX from "xlsx";
import { observer } from "mobx-react-lite";

export const BulkImportSpreadsheet = observer (({ store }) => {
    const handleFileUpload = (event) => {
        const file = event.target.files[0];
        if (!file) return;

        const reader = new FileReader();

        reader.onload = (e) => {
            const data = new Uint8Array(e.target.result);
            const workbook = XLSX.read(data, { type: "array" });

            const firstSheetName = workbook.SheetNames[0];
            const worksheet = workbook.Sheets[firstSheetName];

            const jsonData = XLSX.utils.sheet_to_json(worksheet, { defval: "" });

            const normalized = jsonData.map((row) => ({
                mediaAsset: row.mediaAsset,
                name: row.name,
                date: row.date?.toString() || "", 
                location: row.location,
                submitterID: row.submitterID, 
              }));
            console.log("Parsed JSON:", jsonData);
            if (store && store.setSpreadsheetData) {
                store.setSpreadsheetData(normalized);
            }
        };

        reader.readAsArrayBuffer(file);
    };

    const handleUploadClick = () => {
        document.getElementById("spreadsheet-input").click();
    };

    return (
        <div className="p-2">
            <h5 style={{ fontWeight: "600" }}>
                <FormattedMessage id="SPREADSHEET_SECTION" />
            </h5>
            <p>
                <FormattedMessage id="SUPPORTED_FILETYPES" /> (.csv, .xlsx)
            </p>
            {/* {(
                <pre style={{ whiteSpace: "pre-wrap" }}>
                    {12345}
                    {JSON.stringify(store.spreadsheetData, null, 2)}
                </pre>
            )} */}

            <div className="d-flex flex-column">
                <input
                    type="file"
                    id="spreadsheet-input"
                    accept=".csv,.xlsx"
                    style={{ display: "none" }}
                    onChange={handleFileUpload}
                />
                <MainButton
                    variant="primary"
                    className="mb-2"
                    onClick={handleUploadClick}
                >
                    <FormattedMessage id="UPLOAD_SPREADSHEET" />
                </MainButton>
            </div>
        </div>
    );
});

