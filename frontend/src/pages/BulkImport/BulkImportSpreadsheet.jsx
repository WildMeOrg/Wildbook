import React, { useContext } from "react";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import * as XLSX from "xlsx";
import { observer } from "mobx-react-lite";
import ThemeContext from "../../ThemeColorProvider";
import ProgressBar from "react-bootstrap/ProgressBar";

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
      const firstSheetName = workbook.SheetNames[0];
      const worksheet = workbook.Sheets[firstSheetName];
      const jsonData = XLSX.utils.sheet_to_json(worksheet, { defval: "" });
      store.setRawData(jsonData);
      const totalRows = jsonData.length;
      const processedData = [];
      let currentIndex = 0;

      console.log("submissionId++++++++++++++++", store.submissionId);

      const rowKeys = Object.keys(jsonData[0] || {});
      store.setRawColumns(rowKeys);
      const mediaAssetsCols = rowKeys.filter(k => k.startsWith("Encounter.mediaAsset"));
      const remaining = rowKeys
        .filter(k => !store.specifiedColumns.includes(k))
        .filter(k => !store.removedColumns.includes(k))
        .filter(k => !mediaAssetsCols.includes(k));
      store.setColumnsDef([...store.specifiedColumns, ...remaining]);

      const pad = (n) => n.toString().padStart(2, "0");

      const processChunk = () => {
        const chunk = jsonData.slice(currentIndex, currentIndex + CHUNK_SIZE);
        const normalizedChunk = chunk.map((row) => {
          const year = Number(row["Encounter.year"]);
          const month = Number(row["Encounter.month"]);
          const day = Number(row["Encounter.day"]);
          const hour = Number(row["Encounter.hour"]);
          const minutes = Number(row["Encounter.minutes"]);
          const dt = new Date(year, month - 1, day, hour, minutes);

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

          const mediaAssets = mediaAssetsCols.map((col) => {
            return row[col];
          }).join(", ");

          return {
            "Encounter.mediaAsset0": mediaAssets,
            "Encounter.decimalLatitude": getLatLong(
              row["Encounter.decimalLatitude"],
              row["Encounter.decimalLongitude"],
            ),
            "Encounter.year": `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}:00.000Z`,
            "Encounter.genus":
              row["Encounter.genus"] + " " + row["Encounter.specificEpithet"],
            ...row,
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
    };
    reader.readAsArrayBuffer(file);
  };

  const handleFileUpload = (event) => {
    const file = event.target.files[0];
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
    <div className="p-2">
      <h5 style={{ fontWeight: "600" }}>
        <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET" />
      </h5>
      <p>
        <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET_DESC_PART1" />
        <FormattedMessage id="BULK_IMPORT_UPLOAD_SPREADSHEET_DESC_PART2" />
      </p>

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
          height: "200px",
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
      {store.spreadsheetUploadProgress > 0 &&
        <ProgressBar
          now={store.spreadsheetUploadProgress}
          label={`${store.spreadsheetUploadProgress}%`}
          striped
          animated
        />
      }

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
