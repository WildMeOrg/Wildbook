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

    reader.onerror = (error) => {
      alert(`Error reading file: ${error.message}`);
      store.setSpreadsheetUploadProgress(0);
      store.setSpreadsheetFileName("");
      store.setRawData([]);
      store.setRawColumns([]);
      store.setColumnsDef([]);
      store.setWorksheetInfo(0, [], 0, 0, "");
      store.setValidationErrors({});
      store.setValidationWarnings({});
      store.clearSubmissionErrors();
    };

    reader.onload = (e) => {
      try {
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
        const allJsonDataRaw = XLSX.utils.sheet_to_json(worksheet, {
          defval: "",
        });
        const allJsonData = allJsonDataRaw.map((row) => {
          const normalizedRow = {};
          for (const key in row) {
            const newKey = key
              .replace(/Occurrence/g, "Sighting")
              .replace(/occurrence/g, "sighting");
            normalizedRow[newKey] = row[key];
          }
          return normalizedRow;
        });

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

        const userUploadedCols = new Set(rowKeys);
        const includedSpecified = specifiedColumns.filter((col) =>
          userUploadedCols.has(col),
        );

        store.setColumnsDef([...includedSpecified, ...remaining]);
        if (
          !store.columnsDef.includes("Encounter.mediaAsset0") &&
          mediaAssetsCols.length > 0
        ) {
          store.columnsDef.unshift("Encounter.mediaAsset0");
          store.rawColumns.unshift("Encounter.mediaAsset0");
        }

        store.applyDynamicValidationRules();

        const formatDate = (year, month, day, hour, minute) => {
          const pad2 = (s) =>
            s != null && s !== "" ? String(s).padStart(2, "0") : "";

          const y = String(year);
          const m = pad2(month);
          const d = pad2(day);
          const hh = pad2(hour);
          const mm = pad2(minute);

          let result = y;

          if ((month != null && month !== "") || (day != null && day !== "")) {
            result += "-" + m;
            if (day != null && day !== "") {
              result += "-" + d;
            }
          }

          if (
            (hour != null && hour !== "") ||
            (minute != null && minute !== "")
          ) {
            result += "T" + hh;
            if (minute != null && minute !== "") {
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
            const normalizedRow = { ...row };
            const mediaAssets = mediaAssetsCols
              .filter((v) => row[v] != null && row[v] !== "")
              .map((col) => {
                const mediaAsset =
                  typeof row[col] === "string"
                    ? row[col].trim()
                    : String(row[col] ?? "").trim();
                return mediaAsset;
              })
              .filter((v) => v !== "")
              .join(", ");
            if (mediaAssets) {
              normalizedRow["Encounter.mediaAsset0"] = mediaAssets;
            }

            const Encounter_decimalLatitude = getLatLong(
              row["Encounter.decimalLatitude"],
              row["Encounter.decimalLongitude"],
            );
            if (Encounter_decimalLatitude) {
              normalizedRow["Encounter.decimalLatitude"] =
                Encounter_decimalLatitude;
            }

            const Encounter_latitude = getLatLong(
              row["Encounter.latitude"],
              row["Encounter.longitude"],
            );
            if (Encounter_latitude) {
              normalizedRow["Encounter.latitude"] = Encounter_latitude;
            }

            const Sighting_decimalLatitude = getLatLong(
              row["Sighting.decimalLatitude"],
              row["Sighting.decimalLongitude"],
            );

            if (Sighting_decimalLatitude) {
              normalizedRow["Sighting.decimalLatitude"] =
                Sighting_decimalLatitude;
            }

            const formattedEncounterDate = formatDate(
              row["Encounter.year"] ?? "",
              row["Encounter.month"] ?? "",
              row["Encounter.day"] ?? "",
              row["Encounter.hour"] ?? "",
              row["Encounter.minutes"] ?? "",
            );
            const formattedSightingDate = formatDate(
              row["Sighting.year"] ?? "",
              row["Sighting.month"] ?? "",
              row["Sighting.day"] ?? "",
              row["Sighting.hour"] ?? "",
              row["Sighting.minutes"] ?? "",
            );

            if (
              formattedEncounterDate &&
              formattedEncounterDate !== "undefined"
            ) {
              normalizedRow["Encounter.year"] = formattedEncounterDate;
            } else if (
              formattedSightingDate &&
              formattedSightingDate !== "undefined"
            ) {
              store.columnsDef.splice(1, 0, "Encounter.year");
              store.rawColumns.splice(1, 0, "Encounter.year");
              normalizedRow["Encounter.year"] = formattedSightingDate;
              delete normalizedRow["Sighting.year"];
              delete normalizedRow["Sighting.month"];
              delete normalizedRow["Sighting.day"];
              delete normalizedRow["Sighting.hour"];
              delete normalizedRow["Sighting.minutes"];
              store.setColumnsDef(
                store.columnsDef.filter(
                  (col) =>
                    col !== "Sighting.year" &&
                    col !== "Sighting.month" &&
                    col !== "Sighting.day" &&
                    col !== "Sighting.hour" &&
                    col !== "Sighting.minutes",
                ),
              );
              store.setRawColumns(
                store.rawColumns.filter(
                  (col) =>
                    col !== "Sighting.year" &&
                    col !== "Sighting.month" &&
                    col !== "Sighting.day" &&
                    col !== "Sighting.hour" &&
                    col !== "Sighting.minutes",
                ),
              );
            }
            if (
              formattedEncounterDate &&
              formattedEncounterDate !== "undefined" &&
              formattedSightingDate &&
              formattedSightingDate !== "undefined"
            ) {
              normalizedRow["Sighting.year"] = formattedSightingDate;
            }

            const genus = String(row["Encounter.genus"] ?? "").trim();
            const specific = String(
              row["Encounter.specificEpithet"] ?? "",
            ).trim();
            const genusCombined = [genus, specific].filter(Boolean).join(" ");
            if (genusCombined) {
              normalizedRow["Encounter.genus"] = genusCombined;
            }

            return normalizedRow;
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
              const { errors, warnings } = store.validateSpreadsheet();
              store.setValidationErrors(errors);
              store.setValidationWarnings(warnings);
              store.setActiveStep(2);
            }
          }
        };
        processChunk();
      } catch (error) {
        alert(`Error processing file: ${error.message}`);
        store.setSpreadsheetUploadProgress(0);
        store.setSpreadsheetFileName("");
        store.setRawData([]);
        store.setRawColumns([]);
        store.setColumnsDef([]);
        store.setWorksheetInfo(0, [], 0, 0, "");
        store.setValidationErrors({});
        store.setValidationWarnings({});
        store.clearSubmissionErrors();
      }
    };
    reader.readAsArrayBuffer(file);
  };

  const handleFileUpload = (event) => {
    store.clearSubmissionErrors();
    const file = event.target.files[0];
    const filename = file?.name || "";
    store.setSpreadsheetFileName(filename);
    store.setSpreadsheetUploadProgress(0);
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
    if (!file) return;

    const validExtensions = [".csv", ".xlsx"];
    const lowerName = file.name.toLowerCase();
    if (!validExtensions.some((ext) => lowerName.endsWith(ext))) {
      alert("Please upload a valid CSV or XLSX file.");
      return;
    }

    store.setSpreadsheetFileName(file.name);
    store.setSpreadsheetUploadProgress(0);
    processFile(file);
  };

  const handleUploadClick = () => {
    document.getElementById("spreadsheet-input").click();
  };

  return (
    <div className="mt-4" id="bulk-import-spreadsheet">
      <div
        className="d-flex flex-row justify-content-between"
        id="bulk-import-spreadsheet"
      >
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
            data-testid="spreadsheet-input"
            accept=".csv,.xlsx"
            style={{ display: "none" }}
            onChange={handleFileUpload}
          />
          <MainButton
            id="browse-button"
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
              store.setValidationErrors({});
              store.setValidationWarnings({});
              store.clearSubmissionErrors();
            }}
            className="position-absolute bg-white rounded-circle d-flex align-items-center justify-content-center shadow-sm border"
            style={{
              top: "20px",
              right: "10px",
              cursor: "pointer",
              zIndex: 10,
              height: 25,
              width: 25,
            }}
          >
            <i
              style={{
                color: theme.primaryColors.primary500,
                WebkitTextStroke: "0.5px " + theme.primaryColors.primary500,
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

      <div className="mt-3">
        {store.spreadsheetUploadProgress === 100 ? (
          <FormattedMessage id="BULK_IMPORT_SPREADSHEET_UPLOAD_COMPLETE" />
        ) : store.spreadsheetUploadProgress !== 0 ? (
          `${store.spreadsheetUploadProgress}%`
        ) : null}
      </div>

      <div className="d-flex flex-row justify-content-between mt-4 mb-4">
        <MainButton
          id="previous-button"
          onClick={() => store.setActiveStep(0)}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="PREVIOUS" />
        </MainButton>
        <MainButton
          id="next-button"
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
