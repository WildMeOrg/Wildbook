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
      const totalRows = jsonData.length;
      const processedData = [];
      let currentIndex = 0;

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

          return {
            mediaAsset: row["Encounter.mediaAsset0"],
            IndividualID: row["MarkedIndividual.individualID"],
            occurrenceID: row["Encounter.occurrenceID"],
            occurrenceRemarks: row["Encounter.occurrenceRemarks"],
            location: row["Encounter.verbatimLocality"],
            country: row["Encounter.country"],
            decimalLatitude: row["Encounter.decimalLatitude"],
            decimalLongitude: row["Encounter.decimalLongitude"],
            date: `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}:00.000Z`,
            genus: row["Encounter.genus"],
            species: row["Encounter.specificEpithet"],
            sex: row["Encounter.sex"],
            lifeStage: row["Encounter.lifeStage"],
            livingStatus: row["Encounter.livingStatus"],
            behavior: row["Encounter.behavior"],
            researcherComments: row["Encounter.researcherComments"],
            submitterID: row["Encounter.submitterID"],
            photographerEmail: row["Encounter.photographer0.emailAddress"],
            informOtherEmail: row["Encounter.informOther0.emailAddress"],
            sampleID: row["TissueSample.sampleID"],
            sexAnalysis: row["SexAnalysis.sex"],
          }
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
        <FormattedMessage id="SPREADSHEET_SECTION" />
      </h5>
      <p>
        <FormattedMessage id="SUPPORTED_FILETYPES" /> (.csv, .xlsx)
      </p>

      <div
        className={`d-flex 
                flex-column 
                align-items-center 
                justify-content-center
                border
                ${isDragging ? "border-primary" : "border-secondary"}
                `}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        style={{
          width: "100%",
          height: "200px",
          borderRadius: "10px",
          backgroundColor: isDragging
            ? theme.wildMeColors.cyan100
            : theme.defaultColors.white,
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
          variant="primary"
          className="mb-2"
          onClick={handleUploadClick}
        >
          <FormattedMessage id="UPLOAD_SPREADSHEET" />
        </MainButton>
      </div>
      {
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
