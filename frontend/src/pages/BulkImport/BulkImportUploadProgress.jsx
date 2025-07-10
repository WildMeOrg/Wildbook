import React from "react";
import { observer } from "mobx-react-lite";
import "./BulkImport.css";
import {
  CircularProgressbarWithChildren,
  buildStyles,
} from "react-circular-progressbar";
import "react-circular-progressbar/dist/styles.css";
import { FaImage } from "react-icons/fa";
import ThemeColorContext from "../../ThemeColorProvider";
import { FinishedIcon } from "../../components/FinishedIcon";

export const BulkImportUploadProgress = observer(({ store }) => {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div className="d-flex flex-row mt-4 me-5" id="bulk-import-progress-bar">
      <div
        id="step-image"
        data-testid="step-image"
        style={{ width: 40, height: 40, marginRight: "30px" }}
        onClick={() => {
          store.setActiveStep(0);
        }}
      >
        {store.imageUploadProgress === 100 &&
        store.uploadedImages.length > 0 ? (
          <FinishedIcon />
        ) : (
          <CircularProgressbarWithChildren
            value={store.imageUploadProgress}
            strokeWidth={6}
            background
            backgroundPadding={4}
            styles={buildStyles({
              trailColor: theme.primaryColors.primary50,
              backgroundColor:
                store.imageUploadProgress === 100
                  ? theme.primaryColors.primary50
                  : store.activeStep === 0
                    ? theme.primaryColors.primary500
                    : theme.primaryColors.primary50,
            })}
          >
            {
              <FaImage
                role="img"
                size={20}
                color={
                  store.activeStep === 0
                    ? "#fff"
                    : theme.primaryColors.primary500
                }
              />
            }
          </CircularProgressbarWithChildren>
        )}
      </div>
      <div
        data-testid="step-spreadsheet"
        style={{ width: 40, height: 40, marginRight: "30px" }}
        onClick={() => {
          store.setActiveStep(1);
        }}
      >
        {store.spreadsheetUploadProgress === 100 ? (
          <FinishedIcon />
        ) : (
          <CircularProgressbarWithChildren
            value={store.spreadsheetUploadProgress}
            strokeWidth={6}
            background
            backgroundPadding={4}
            styles={buildStyles({
              trailColor: theme.primaryColors.primary50,
              backgroundColor:
                store.spreadsheetUploadProgress === 100
                  ? theme.primaryColors.primary50
                  : store.activeStep === 1
                    ? theme.primaryColors.primary500
                    : theme.primaryColors.primary50,
            })}
            onClick={() => {
              store.setActiveStep(1);
            }}
          >
            <svg
              width="20"
              height="20"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M19.5 3H4.5C3.4 3 2.5 3.9 2.5 5V19C2.5 20.1 3.4 21 4.5 21H19.5C20.6 21 21.5 20.1 21.5 19V5C21.5 3.9 20.6 3 19.5 3ZM19.5 5V8H4.5V5H19.5ZM14.5 19H9.5V10H14.5V19ZM4.5 10H7.5V19H4.5V10ZM16.5 19V10H19.5V19H16.5Z"
                fill={
                  store.activeStep === 1
                    ? "#fff"
                    : theme.primaryColors.primary500
                }
              />
            </svg>
          </CircularProgressbarWithChildren>
        )}
      </div>
      <div
        style={{
          width: 38,
          height: 38,
          marginRight: "30px",
          backgroundColor:
            store.activeStep === 2
              ? theme.primaryColors.primary500
              : theme.primaryColors.primary50,
          borderRadius: "50%",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          color:
            store.activeStep === 2 ? "#fff" : theme.primaryColors.primary500,
          fontSize: "20px",
        }}
        onClick={() => {
          if (store.spreadsheetUploadProgress === 100) {
            store.setActiveStep(2);
          }
        }}
      >
        {Object.keys(store.validationErrors).length === 0 &&
        store.spreadsheetUploadProgress === 100 &&
        store.missingRequiredColumns.length === 0 ? (
          <FinishedIcon />
        ) : (
          <i className="bi bi-eye"></i>
        )}
      </div>
      <div
        style={{
          width: 38,
          height: 38,
          marginRight: "30px",
          backgroundColor:
            store.activeStep === 3
              ? theme.primaryColors.primary500
              : theme.primaryColors.primary50,
          borderRadius: "50%",
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          color:
            store.activeStep === 3 ? "#fff" : theme.primaryColors.primary500,
          fontSize: "20px",
        }}
        onClick={() => {
          if (store.spreadsheetUploadProgress === 100) {
            store.setActiveStep(3);
          }
        }}
      >
        <i className="bi bi-crosshair"></i>
      </div>
    </div>
  );
});
