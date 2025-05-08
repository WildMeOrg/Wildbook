import React, { useCallback } from "react";
import { observer } from "mobx-react-lite";
import EditableDataTable from "./EditableDataTable";
import { useContext } from "react";
import ThemeContext from "../../ThemeColorProvider";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";

export const BulkImportTableReview = observer(({ store }) => {
  const theme = useContext(ThemeContext);
  const { submit, isLoading, error } = usePostBulkImport();

  const handleStartImport = () => {
    console.log("Starting import with data:", JSON.stringify(store.spreadsheetData));
    submit(store.submissionId, store.rawColumns, store.spreadsheetData)
      .then(result => {
        store.bulkImportId = result.id;
        store.setActiveStep(3);
      })
      .catch(err => {
        console.error('Import failed', err);
      });
  };

  return (
    <div>
      <EditableDataTable store={store} />
      {error && <div className="alert alert-danger">{error}</div>}
      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            // store.setActiveStep(3);
            handleStartImport();
          }}
          // disabled={store.isSubmitting || store._uploadFinished || Object.keys(store.validateSpreadsheet()).length > 0}
          disabled={store.isSubmitting || store._uploadFinished}
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
