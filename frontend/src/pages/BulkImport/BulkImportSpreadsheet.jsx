import React, { useContext } from "react";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import * as XLSX from "xlsx";
import { observer } from "mobx-react-lite";
import ThemeContext from "../../ThemeColorProvider";
import ProgressBar from "react-bootstrap/ProgressBar";
import { specifiedColumns, removedColumns } from "./BulkImportConstants";
import BulkImportSeeInstructionsButton from "./BulkImportSeeInstructionsButton";

export const BulkImportSpreadsheet = observer(({ store }) => {
  const theme = useContext(ThemeContext);

  const CHUNK_SIZE = 5;
  const [isDragging, setIsDragging] = React.useState(false);

  const processFile = (file) => {
    if (!file) return;
    const reader = new FileReader();
    reader.onload = (e) => {
      const data = new Uint8Array(e.target.result);
      const workbook = XLSX.read(data, { type: "array" });
      const sheetNames = workbook.SheetNames;

      const sheetStats = sheetNames.map((name) => {
        const ws = workbook.Sheets[name];
        const arr = XLSX.utils.sheet_to_json(ws, { header: 1, defval: "" });
        const colCount = arr[0]?.length || 0;
        const rowCount = arr.length - 1;
        return { name, rowCount, colCount };
      });

      const totalRows = sheetStats.reduce((sum, s) => sum + s.rowCount, 0);
      const maxCols = Math.max(...sheetStats.map((s) => s.colCount), 0);

      store.setWorksheetInfo(
        sheetNames.length,
        sheetNames,
        maxCols,
        totalRows,
        file.name,
      );

      const firstSheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[firstSheetName];
      const allJsonData = XLSX.utils.sheet_to_json(worksheet, { defval: "" });
      store.setRawData(allJsonData || []);
      const processedData = [];
      let currentIndex = 0;

      const rowKeys = Object.keys(allJsonData[0] || {});
      store.setRawColumns(rowKeys);
      const mediaAssetsCols = rowKeys.filter(
        (k) =>
          k.startsWith("Encounter.mediaAsset") && k.split(".").length === 2,
      );
      const remaining = rowKeys
        .filter((k) => !specifiedColumns.includes(k))
        .filter((k) => !removedColumns.includes(k))
        .filter((k) => !mediaAssetsCols.includes(k));
      store.setColumnsDef([...specifiedColumns, ...remaining]);
      store.applyDynamicValidationRules();

      const formatDate = (year, month, day, hour, minute) => {
        console.log("working");
        const pad2 = (s) =>
          s != null && s !== "" ? String(s).padStart(2, "0") : "";

        const y = String(year);
        const m = pad2(month);
        const d = pad2(day);
        const hh = pad2(hour);
        const mm = pad2(minute);

        let result = y;
        if (month || day) {
          result += "-" + m;
          if (day) {
            result += "-" + d;
          }
        }
        if (hour || minute) {
          result += "T" + hh;
          if (minute) {
            result += ":" + mm;
          }
        }

        return result;
      };

      const getLatLong = (lat, lon) => {
        const hasLat = lat !== undefined && lat !== null && lat !== "";
        const hasLon = lon !== undefined && lon !== null && lon !== "";

        if (hasLat && hasLon) {
          return `${lat}, ${lon}`;
        } else if (hasLat) {
          return `${lat}, `;
        } else if (hasLon) {
          return `, ${lon}`;
        } else {
          return "";
        }
      };
      const processChunk = () => {
        const chunk = allJsonData.slice(
          currentIndex,
          currentIndex + CHUNK_SIZE,
        );
        const normalizedChunk = chunk.map((row) => {
          const mediaAssets = mediaAssetsCols
            .filter((v) => row[v] != null && row[v] !== "")
            .map((col) => {
              const mediaAsset = row[col].trim();
              return mediaAsset;
            })
            .join(", ");

          return {
            ...row,
            "Encounter.mediaAsset0": mediaAssets,
            "Encounter.decimalLatitude": getLatLong(
              row["Encounter.decimalLatitude"],
              row["Encounter.decimalLongitude"],
            ),
            "Encounter.year": formatDate(
              row["Encounter.year"],
              row["Encounter.month"],
              row["Encounter.day"],
              row["Encounter.hour"],
              row["Encounter.minutes"],
            ),
            "Encounter.genus":
              row["Encounter.genus"].trim() +
              " " +
              row["Encounter.specificEpithet"].trim(),
          };
        });

        processedData.push(...normalizedChunk);
        currentIndex += CHUNK_SIZE;

        store.setSpreadsheetUploadProgress(
          Math.min(100, Math.round((currentIndex / totalRows) * 100)),
        );
        if (currentIndex < totalRows) {
          setTimeout(processChunk, 0);
        } else {
          if (store && store.setSpreadsheetData) {
            store.setSpreadsheetData(processedData);
          }
        }
      };
      processChunk();
      store.updateRawFromNormalizedRow();
    };
    reader.readAsArrayBuffer(file);
  };

  const handleFileUpload = (event) => {
    const file = event.target.files[0];
    const filename = file?.name || "";
    store.setSpreadsheetFileName(filename);
    store.setSpreadsheetUploadProgress(0);
    console.log("Processing file %:", store.spreadsheetUploadProgress);
    if (!file) return;
    processFile(file);
  };

  const handleDragOver = (event) => {
    event.preventDefault();
    setIsDragging(true);
  };
  const handleDragLeave = (event) => {
    event.preventDefault();
    setIsDragging(false);
  };
  const handleDrop = (event) => {
    event.preventDefault();
    setIsDragging(false);
    const file = event.dataTransfer.files[0];
    if (file) {
      processFile(file);
    }
  };
  const handleUploadClick = () => {
    document.getElementById("spreadsheet-input").click();
  };

  return (
    <div className="mt-4">
      <div className="d-flex flex-row justify-content-between">
        <div>
          <h5 style={{ fontWeight: "600" }}>
            <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET" />
          </h5>
          <p>
            <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET_DESC_PART1" />
            <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET_DESC_PART2" />
          </p>
        </div>
        <BulkImportSeeInstructionsButton store={store} />
      </div>

      {store.spreadsheetUploadProgress === 0 ? (
        <div
          className={`d-flex 
                flex-column 
                align-items-center 
                justify-content-center
                `}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          style={{
            width: "100%",
            height: "300px",
            borderRadius: "10px",
            border: `1px dashed ${theme.primaryColors.primary500}`,
            backgroundColor: isDragging
              ? theme.primaryColors.primary100
              : theme.primaryColors.primary50,
            transition: "background-color 0.3s ease",
          }}
        >
          <input
            type="file"
            id="spreadsheet-input"
            accept=".csv,.xlsx"
            style={{ display: "none" }}
            onChange={handleFileUpload}
          />
          <MainButton
            backgroundColor={theme.wildMeColors.cyan700}
            color={theme.defaultColors.white}
            noArrow={true}
            style={{ width: "auto", fontSize: "1rem", margin: "0 auto" }}
            onClick={handleUploadClick}
          >
            <FormattedMessage id="BROWSE" />
          </MainButton>
        </div>
      ) : (
        <div
          className="mt-4 d-flex flex-column "
          style={{
            position: "relative",
            width: "300px",
            backgroundColor: theme.primaryColors.primary50,
            borderRadius: "10px",
            overflow: "hidden",
          }}
        >
          <div
            id="close-button"
            onClick={() => {
              store.setSpreadsheetUploadProgress(0);
              store.setSpreadsheetFileName("");
              store.setSpreadsheetData([]);
              store.setRawData([]);
              store.setRawColumns([]);
              store.setColumnsDef([]);
              store.setWorksheetInfo(0, [], 0, 0, "");
            }}
            style={{
              position: "absolute",
              top: "20px",
              right: "10px",
              cursor: "pointer",
              zIndex: 1,
            }}
          >
            <i
              style={{
                color: theme.primaryColors.primary500,
              }}
              className="bi bi-x-lg"
            ></i>
          </div>
          <div className="d-flex flex-row align-items-center ps-3 pt-1">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              x="0px"
              y="0px"
              width="50"
              height="50"
              viewBox="0 0 50 50"
            >
              <path
                fill="#4CAF50"
                d="M41,10H25v28h16c0.553,0,1-0.447,1-1V11C42,10.447,41.553,10,41,10z"
              ></path>
              <path
                fill="#FFF"
                d="M32 15H39V18H32zM32 25H39V28H32zM32 30H39V33H32zM32 20H39V23H32zM25 15H30V18H25zM25 25H30V28H25zM25 30H30V33H25zM25 20H30V23H25z"
              ></path>
              <path fill="#2E7D32" d="M27 42L6 38 6 10 27 6z"></path>
              <path
                fill="#FFF"
                d="M19.129,31l-2.411-4.561c-0.092-0.171-0.186-0.483-0.284-0.938h-0.037c-0.046,0.215-0.154,0.541-0.324,0.979L13.652,31H9.895l4.462-7.001L10.274,17h3.837l2.001,4.196c0.156,0.331,0.296,0.725,0.42,1.179h0.04c0.078-0.271,0.224-0.68,0.439-1.22L19.237,17h3.515l-4.199,6.939l4.316,7.059h-3.74V31z"
              ></path>
            </svg>
            <span>{store.spreadsheetFileName} </span>
          </div>
          <ProgressBar
            now={store.spreadsheetUploadProgress}
            variant="info"
            label={
              store.spreadsheetUploadProgress === 100 ? (
                <FormattedMessage id="COMPLETE" />
              ) : (
                `${store.spreadsheetUploadProgress}%`
              )
            }
            style={{
              height: "10px",
              // marginTop: "10px",
              borderRadius: " 0  0 10px 10px",
              backgroundColor: theme.primaryColors.primary50,
            }}
          >
            <div
              className="progress-bar"
              role="progressbar"
              style={{
                width: `${store.spreadsheetUploadProgress}%`,
                backgroundColor: theme.primaryColors.primary500,
              }}
            />
          </ProgressBar>
        </div>
      )}
      {store.spreadsheetUploadProgress === 100 && (
        <div className="mt-2">
          <FormattedMessage id="BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE" />
        </div>
      )}

      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => store.setActiveStep(0)}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="PREVIOUS" />
        </MainButton>
        <MainButton
          onClick={() => store.setActiveStep(2)}
          backgroundColor={theme.wildMeColors.cyan700}
          disabled={store.spreadsheetUploadProgress !== 100}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="NEXT" />
        </MainButton>
      </div>
    </div>
  );
});
