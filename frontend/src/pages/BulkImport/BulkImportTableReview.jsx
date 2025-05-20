import React, { useCallback } from "react";
import { observer } from "mobx-react-lite";
import EditableDataTable from "./EditableDataTable";
import { useContext } from "react";
import ThemeContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";
import { v4 as uuidv4 } from "uuid";
import { BulkImportImageUploadInfo } from "./BulkImportImageUploadInfo";
import { BulkImportSpreadsheetUploadInof } from "./BulkImportSpreadsheetUploadInof";
import BulkImportSeeInstructionsButton from "./BulkImportSeeInstructionsButton";

export const BulkImportTableReview = observer(({ store }) => {
  const theme = useContext(ThemeContext);
  const { submit, isLoading } = usePostBulkImport();
  const submissionId = store.submissionId || uuidv4();
  const hasSubmissionErrors = store.submissionErrors && Object.keys(store.submissionErrors).length > 0;

  const handleStartImport = useCallback(async () => {
    store.clearSubmissionErrors();
    try {
      const result = await submit(submissionId, store.rawColumns, store.rawData);
      if (result?.success) {
        store.resetToDefaults();
      }
    } catch (err) {
      const errors = err.response?.data?.errors;
      console.log("Error during import:", err);
      if (errors) {
        store.setSubmissionErrors(errors);
      } else {
        console.error('Import failed', err);
      }
    }
  });

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
        <BulkImportSpreadsheetUploadInof store={store} />
      </div>
      <EditableDataTable store={store} />
      {hasSubmissionErrors && <div className="alert alert-danger">
        Errors:
        <ul>
          {
            store.submissionErrors.map((error, index) => (
              <li key={index}>
                { `field: ${error.fieldName}, row number: ${error.rowNumber}, error: ${error.type}` }
              </li>
            ))

          }
        </ul>
        
        </div>}
      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            handleStartImport();
          }}
          // disabled={store.isSubmitting || store.spreadsheetUploadProgress !== 100 || Object.keys(store.validateSpreadsheet()).length > 0}
          // disabled={store.isSubmitting || store.imageUploadProgress !== 100 || store.spreadsheetUploadProgress !== 100}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem", marginLeft: "auto" }}
        >
          {isLoading
            ? <FormattedMessage id="LOADING" defaultMessage="Loading..." />
            : <FormattedMessage id="START_BULK_IMPORT" />}
        </MainButton>
      </div>
    </div>
  );
});
