import React from "react";
import { observer } from "mobx-react-lite";
import EditableDataTable from "./EditableDataTable";
import { useContext } from "react";
import ThemeContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import { BulkImportImageUploadInfo } from "./BulkImportImageUploadInfo";
import { BulkImportSpreadsheetUploadInfo } from "./BulkImportSpreadsheetUploadInfo";
import BulkImportSeeInstructionsButton from "./BulkImportSeeInstructionsButton";
import ErrorSummaryBar from "./BulkImportErrorSummaryBar";

export const BulkImportTableReview = observer(({ store }) => {
  const theme = useContext(ThemeContext);
  const hasSubmissionErrors =
    store.submissionErrors && Object.keys(store.submissionErrors).length > 0;

  return (
    <div className="mt-4">
      <div>
        <div>
          <div className="d-flex flex-row justify-content-between">
            <div>
              <h5 style={{ fontWeight: "600" }}>
                <FormattedMessage id="BULK_IMPORT_TABLE_REVIEW" />
              </h5>
              <p>
                <FormattedMessage id="BULK_IMPORT_TABLE_REVIEW_DESC" />
              </p>
            </div>
            <BulkImportSeeInstructionsButton store={store} />
          </div>
        </div>
      </div>
      <div className="d-flex flex-row ">
        <BulkImportImageUploadInfo store={store} />
        <BulkImportSpreadsheetUploadInfo store={store} />
      </div>
      <EditableDataTable store={store} />
      <ErrorSummaryBar store={store} />
      {/* {hasSubmissionErrors && (
        <div className="alert alert-danger">
          Errors:
          <ul>
            {store.submissionErrors.map((error, index) => (
              <li key={index}>
                {`field: ${error.fieldName}, row number: ${error.rowNumber}, error: ${error.type}`}
              </li>
            ))}
          </ul>
        </div>
      )} */}
      {/* {hasSubmissionErrors && (
        <div className="alert alert-danger">
          <strong>There are some issues with your submission:</strong>
          <ul className="list-unstyled mb-0 ps-3">
            {store.submissionErrors.map((error, index) => {
              const readableTypeMap = {
                REQUIRED: "This field is required",
                INVALID_VALUE: "The value is invalid",
                UNKNOWN_FIELD: "Unknown field",
                INVALID_FORMAT: "The format is incorrect",
                MISSING_FILE: "File is missing",
              };

              const message =
                readableTypeMap[error.type] || error.type || "Unknown error";

              return (
                <li key={index}>
                  {`Row ${error.rowNumber + 1}: "${error.fieldName}" — ${message}`}
                </li>
              );
            })}
          </ul>
        </div>
      )} */}

      {hasSubmissionErrors && (
        <div className="alert alert-danger">
          <strong>There are some issues with your submission:</strong>
          <ul className="list-unstyled mb-0 ps-3">
            {store.submissionErrors.map((err, idx) => {
              if ("rowNumber" in err && "fieldName" in err) {
                const row = `Row ${err.rowNumber + 1}`;
                const field = `"${err.fieldName}"`;
                const message =
                  Array.isArray(err.errors) && err.errors.length > 0
                    ? err.errors.map((e) => e.details).join("; ")
                    : err.details || err.type || "Unknown error";
                return <li key={idx}>{`${row}: ${field} — ${message}`}</li>;
              } else if ("filename" in err) {
                return (
                  <li key={idx}>{`Image "${err.filename}": ${err.error}`}</li>
                );
              } else {
                return <li key={idx}>{JSON.stringify(err)}</li>;
              }
            })}
          </ul>
        </div>
      )}

      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            store.setActiveStep(3);
          }}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem", marginLeft: "auto" }}
        >
          <FormattedMessage id="SET_LOCATION" />
        </MainButton>
      </div>
    </div>
  );
});
