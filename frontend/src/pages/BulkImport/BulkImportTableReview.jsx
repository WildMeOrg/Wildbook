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
import { stopPersisting, clearPersistedStore } from 'mobx-persist-store';
import { runInAction } from "mobx";

export const BulkImportTableReview = observer(({ store }) => {
  const theme = useContext(ThemeContext);
  const { submit, isLoading, error: postError } = usePostBulkImport();

  const submissionId = store.submissionId || uuidv4();
  const hasSubmissionErrors = store.submissionErrors && Object.keys(store.submissionErrors).length > 0;

  const handleStartImport = useCallback(async () => {
    console.log("Starting import with data:", JSON.stringify(store.spreadsheetData));
    store.clearSubmissionErrors();
    try {
      const result = await submit(submissionId, store.rawColumns, store.spreadsheetData);

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
    <div >
      <div className="d-flex flex-row ">
        <BulkImportImageUploadInfo store={store} />
        <BulkImportSpreadsheetUploadInof store={store} />
      </div>
      <EditableDataTable store={store} />
      {store.submissionErrors && <div className="alert alert-danger">{JSON.stringify(store.submissionErrors)}</div>}
      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            // store.setActiveStep(3);
            handleStartImport();
          }}
          // disabled={store.isSubmitting || store.imageUploadProgress !== 100  || Object.keys(store.validateSpreadsheet()).length > 0}
          disabled={store.isSubmitting || store.imageUploadProgress !== 100 || store.spreadsheetUploadProgress !== 100}
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
