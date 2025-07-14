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
import { useIntl } from "react-intl";

export const BulkImportTableReview = observer(({ store }) => {
  const intl = useIntl();
  const theme = useContext(ThemeContext);
  const hasSubmissionErrors =
    store.submissionErrors && Object.keys(store.submissionErrors).length > 0;
  let tableErrors = [];
  let rowErrors = [];
  const renderErrorCode = (code) =>
    intl.formatMessage({
      id: `BULK_IMPORT_ERROR_${code}`,
      defaultMessage: code,
    });

  if (hasSubmissionErrors) {
    tableErrors = store.submissionErrors.filter(
      (e) => !e.rowNumber && e.fieldNames,
    );
    rowErrors = store.submissionErrors.filter(
      (e) => typeof e.rowNumber === "number",
    );
  }

  return (
    <div className="mt-4" id="bulk-import-table-review">
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

      {store.missingRequiredColumns.length > 0 && (
        <div className="text-danger mt-3">
          <strong>
            <FormattedMessage
              id="BULK_IMPORT_MISSING_REQUIRED_COLUMNS"
              defaultMessage="Required fields missing: "
            />
          </strong>
          {store.missingRequiredColumns.join(",")}
        </div>
      )}

      <EditableDataTable store={store} />
      <ErrorSummaryBar store={store} />

      {hasSubmissionErrors && (
        <div className="alert alert-danger">
          <strong>
            <FormattedMessage
              id="SUBMISSION_ISSUE_HEADER"
              defaultMessage="There are some issues with your submission:"
            />
          </strong>

          {store.submissionErrors.length > 0 && (
            <ul>
              {tableErrors.map((error, index) => {
                const name =
                  error.fieldNames?.length > 0
                    ? error.fieldNames?.join(", ")
                    : error.fieldName;
                return (
                  <li key={index}>
                    {renderErrorCode(error.type)}: {name}
                  </li>
                );
              })}
              {rowErrors.map((error, index) => {
                const name =
                  error?.fieldNames?.length > 0
                    ? error?.fieldNames?.join(", ")
                    : error.fieldName;
                return (
                  <li key={index}>
                    <FormattedMessage id="ROW" /> {Number(error.rowNumber) + 1}:{" "}
                    {renderErrorCode(error.type)} {": "}
                    {name}
                  </li>
                );
              })}
            </ul>
          )}
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
