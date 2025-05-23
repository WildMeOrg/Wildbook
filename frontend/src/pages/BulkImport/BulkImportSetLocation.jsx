import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useContext, useCallback } from "react";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";
import { v4 as uuidv4 } from "uuid";

export const BulkImportSetLocation = observer(({ store }) => {
    const theme = useContext(ThemeContext);
    const { submit, isLoading } = usePostBulkImport();
    const submissionId = store.submissionId || uuidv4();
    const hasSubmissionErrors = store.submissionErrors && Object.keys(store.submissionErrors).length > 0;

    console.log("BulkImportSetLocation component rendered");
    const handleStartImport = useCallback(async () => {
        store.clearSubmissionErrors();
        try {
            const result = await submit(submissionId, store.rawColumns, store.rawData);
            if (result?.success) {
                // store.resetToDefaults();
                alert("Import successful");
            }
        } catch (err) {
            alert("Import failed");
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
        <div className="d-flex flex-column mt-4">
            <h2>
                <FormattedMessage id="BULK_IMPORT_SET_LOCATION" />
            </h2>
            <p>
                <FormattedMessage id="BULK_IMPORT_SET_LOCATION_DESC" />
            </p>
            <div style={{
                width: "500px"
            }}>
                <select>
                    <option value="location1">Location 1</option>
                    <option value="location2">Location 2</option>
                    <option value="location3">Location 3</option>
                </select>
            </div>

            <div className="d-flex flex-row justify-content-between mt-4">
                <MainButton
                    onClick={() => {
                        handleStartImport();
                    }}
                    disabled={store.isSubmitting || store.spreadsheetUploadProgress !== 100 || Object.keys(store.validateSpreadsheet()).length > 0}
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