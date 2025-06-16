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
      {/* <ErrorSummaryBar store={store} /> */}
      {hasSubmissionErrors && (
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
