// import React from "react";
// import { FormattedMessage } from "react-intl";
// import MainButton from "../../components/MainButton";
// import * as XLSX from "xlsx";
// import { observer } from "mobx-react-lite";
// import { useContext } from "react";
// import ThemeContext from "../../ThemeColorProvider";

// export const BulkImportSpreadsheet = observer(({ store }) => {
//     const theme = useContext(ThemeContext);

//     const handleFileUpload = (event) => {
//         const file = event.target.files[0];
//         if (!file) return;

//         const reader = new FileReader();

//         reader.onload = (e) => {
//             const data = new Uint8Array(e.target.result);
//             const workbook = XLSX.read(data, { type: "array" });

//             const firstSheetName = workbook.SheetNames[0];
//             const worksheet = workbook.Sheets[firstSheetName];

//             const jsonData = XLSX.utils.sheet_to_json(worksheet, { defval: "" });

//             const normalized = jsonData.map((row) => ({
//                 mediaAsset: row.mediaAsset,
//                 name: row.name,
//                 date: row.date?.toString() || "",
//                 location: row.location,
//                 submitterID: row.submitterID,
//             }));
//             console.log("Parsed JSON:", jsonData);
//             if (store && store.setSpreadsheetData) {
//                 store.setSpreadsheetData(normalized);
//             }
//         };

//         reader.readAsArrayBuffer(file);
//     };

//     const handleUploadClick = () => {
//         document.getElementById("spreadsheet-input").click();
//     };

//     return (
//         <div className="p-2">
//             <h5 style={{ fontWeight: "600" }}>
//                 <FormattedMessage id="SPREADSHEET_SECTION" />
//             </h5>
//             <p>
//                 <FormattedMessage id="SUPPORTED_FILETYPES" /> (.csv, .xlsx)
//             </p>
//             {/* {(
//                 <pre style={{ whiteSpace: "pre-wrap" }}>
//                     {12345}
//                     {JSON.stringify(store.spreadsheetData, null, 2)}
//                 </pre>
//             )} */}

//             <div className="d-flex flex-column">
//                 <input
//                     type="file"
//                     id="spreadsheet-input"
//                     accept=".csv,.xlsx"
//                     style={{ display: "none" }}
//                     onChange={handleFileUpload}
//                 />
//                 <MainButton
//                     variant="primary"
//                     className="mb-2"
//                     onClick={handleUploadClick}
//                 >
//                     <FormattedMessage id="UPLOAD_SPREADSHEET" />
//                 </MainButton>
//             </div>
//             <div>
//                 <MainButton
//                     onClick={() => {
//                         store.setActiveStep(0);
//                     }}
//                     backgroundColor={theme.wildMeColors.cyan700}
//                     color={theme.defaultColors.white}
//                     noArrow={true}
//                     style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
//                 >
//                     <FormattedMessage id="PREVIOUS" />
//                 </MainButton>
//                 <MainButton
//                     onClick={() => {
//                         store.setActiveStep(2);
//                     }}
//                     backgroundColor={theme.wildMeColors.cyan700}
//                     color={theme.defaultColors.white}
//                     noArrow={true}
//                     style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
//                 >
//                     <FormattedMessage id="NEXT" />
//                 </MainButton>
//             </div>
//         </div>
//     );
// });

import React, { useState, useContext } from "react";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import * as XLSX from "xlsx";
import { observer } from "mobx-react-lite";
import ThemeContext from "../../ThemeColorProvider";
import ProgressBar from "react-bootstrap/ProgressBar";

export const BulkImportSpreadsheet = observer(({ store }) => {
    const theme = useContext(ThemeContext);
    // const [progress, setProgress] = useState(0);
    const [uploading, setUploading] = useState(false);

    const CHUNK_SIZE = 5;

    const handleFileUpload = (event) => {
        const file = event.target.files[0];
        if (!file) return;

        setUploading(true);
        // setProgress(0);

        const reader = new FileReader();
        reader.onload = (e) => {
            const data = new Uint8Array(e.target.result);
            const workbook = XLSX.read(data, { type: "array" });
            const firstSheetName = workbook.SheetNames[0];
            const worksheet = workbook.Sheets[firstSheetName];
            const jsonData = XLSX.utils.sheet_to_json(worksheet, { defval: "" });

            const totalRows = jsonData.length;
            const processedData = [];
            let currentIndex = 0;

            const processChunk = () => {
                const chunk = jsonData.slice(currentIndex, currentIndex + CHUNK_SIZE);
                const normalizedChunk = chunk.map((row) => ({
                    mediaAsset: row.mediaAsset,
                    name: row.name,
                    date: row.date?.toString() || "",
                    location: row.location,
                    submitterID: row.submitterID,
                }));

                processedData.push(...normalizedChunk);
                currentIndex += CHUNK_SIZE;

                store.setSpreadsheetUploadProgress(Math.min(100, Math.round((currentIndex / totalRows) * 100)));

                if (currentIndex < totalRows) {
                    setTimeout(processChunk, 0);
                } else {
                    if (store && store.setSpreadsheetData) {
                        store.setSpreadsheetData(processedData);
                    }
                    setTimeout(() => setUploading(false), 300);
                }
            };

            processChunk();
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

                {store.spreadsheetUploadProgress !== 0 && <ProgressBar now={store.spreadsheetUploadProgress} label={`${store.spreadsheetUploadProgress}%`} striped animated />}

            </div>

            <div className="d-flex flex-row justify-content-between mt-4">
                <MainButton
                    onClick={() => store.setActiveStep(0)}
                    backgroundColor={theme.wildMeColors.cyan700}
                    color={theme.defaultColors.white}
                    noArrow={true}
                    style={{ width: "auto", fontSize: "1rem",  }}
                >
                    <FormattedMessage id="PREVIOUS" />
                </MainButton>
                <MainButton
                    onClick={() => store.setActiveStep(2)}
                    backgroundColor={theme.wildMeColors.cyan700}
                    color={theme.defaultColors.white}
                    noArrow={true}
                    style={{ width: "auto", fontSize: "1rem",  }}
                >
                    <FormattedMessage id="NEXT" />
                </MainButton>
            </div>
        </div>
    );
});
