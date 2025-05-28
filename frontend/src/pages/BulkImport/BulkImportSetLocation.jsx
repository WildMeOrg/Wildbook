import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useContext, useCallback } from "react";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";
import { v4 as uuidv4 } from "uuid";
import Select from "react-select";

export const BulkImportSetLocation = observer(({ store }) => {
    const theme = useContext(ThemeContext);
    const { submit, isLoading } = usePostBulkImport();
    const submissionId = store.submissionId || uuidv4();
    const hasSubmissionErrors = store.submissionErrors && Object.keys(store.submissionErrors).length > 0;

    console.log("locationID", store.locationID);
    const handleStartImport = useCallback(async () => {
        store.clearSubmissionErrors();
        try {
            const result = await submit(submissionId, store.rawColumns, store.rawData, store.spreadsheetFileName);
            if (result?.success) {
                store.resetToDefaults();
                alert("Import successful");
                localStorage.removeItem("BulkImportStore");
                store.setActiveStep(0); // Move to the next step after successful import
                // localStorage.setItem("lastBulkImportTask", result.bulkImportId);
                localStorage.setItem("lastBulkImportTask", submissionId);
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
                <Select
                    isMulti={false}
                    options={store.validLocationIDs.map((location) => ({
                        value: location,
                        label: location
                    }
                    ))}
                    placeholder={<FormattedMessage id="SELECT_LOCATION" defaultMessage="Select Location" />}
                    noOptionsMessage={() => <FormattedMessage id="NO_LOCATIONS_FOUND" defaultMessage="No locations found" />}
                    isClearable={true}
                    isSearchable={true}
                    className="basic-multi-select"
                    classNamePrefix="select"
                    menuPlacement="auto"
                    menuPortalTarget={document.body}
                    value={store.locationID}
                    onChange={(selectedOption) => {
                        console.log("Selected location:", selectedOption);
                        store.setLocationID(selectedOption ? selectedOption.value : null);
                        console.log("Updated store locationID:", store.locationID);
                    }
                    }
                />
            </div>

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