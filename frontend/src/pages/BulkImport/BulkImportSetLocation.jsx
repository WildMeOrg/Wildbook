import React, { useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useContext, useCallback } from "react";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";
import { v4 as uuidv4 } from "uuid";
import Select from "react-select";
import { reaction } from "mobx";
import SuccessModal from "./BulkImportSuccessModal";
import { useState } from "react";
import dayjs from "dayjs";
import FailureModal from "./BulkImportFailureModal";


export const BulkImportSetLocation = observer(({ store }) => {
    const theme = useContext(ThemeContext);
    const { submit, isLoading } = usePostBulkImport();
    const submissionId = store.submissionId || uuidv4();
    const hasSubmissionErrors = store.submissionErrors && Object.keys(store.submissionErrors).length > 0;
    const [showSuccessModal, setShowSuccessModal] = useState(false);
    const [showFailureModal, setShowFailureModal] = useState(false);
    const [lastEditedDate, setLastEditedDate] = useState(dayjs().format("YYYY-MM-DD"));
    const options = React.useMemo(
        () =>
            store.validLocationIDs.map(id => ({
                value: id,
                label: id,
            })),
        [store.validLocationIDs]
    );

    useEffect(() => {
        const disposer = reaction(
            () => store.spreadsheetData.map(row => row["Encounter.locationID"]),
            (locationIDs) => {
                const uniqueIDs = Array.from(
                    new Set(locationIDs.filter(id => id && id.length > 0))
                );
                store.setLocationID(uniqueIDs);
            },
            { fireImmediately: true }
        );

        return () => disposer();
    }, []);


    const selectedOptions = React.useMemo(
        () =>
            options.filter(o => store.locationID?.includes(o.value)),
        [options, store.locationID]
    );

    const handleStartImport = useCallback(async () => {
        store.updateRawFromNormalizedRow(); 
        store.clearSubmissionErrors();
        try {
            const result = await submit(submissionId, store.rawColumns, store.rawData, store.spreadsheetFileName);
            if (result?.success) {
                localStorage.removeItem("BulkImportStore");
                localStorage.setItem("lastBulkImportTask", result.bulkImportId);
                setLastEditedDate(dayjs().format("YYYY-MM-DD"));
                setShowSuccessModal(true);
            }
        } catch (err) {
            const errors = err.response?.data?.errors;
            console.log("Error during import:", err);
            if (errors) {
                // store.setSubmissionErrors(JSON.stringify(errors, null, 2));
                store.setSubmissionErrors(errors);
            } else {
                console.error('Import failed', err);
            }
            setShowFailureModal(true);
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
                    isMulti={true}
                    options={options}
                    placeholder={<FormattedMessage id="SELECT_LOCATION" defaultMessage="Select Location" />}
                    noOptionsMessage={() => <FormattedMessage id="NO_LOCATIONS_FOUND" defaultMessage="No locations found" />}
                    isClearable={true}
                    isSearchable={true}
                    className="basic-multi-select"
                    classNamePrefix="select"
                    menuPlacement="auto"
                    menuPortalTarget={document.body}
                    value={selectedOptions}
                    onChange={(selectedOptions) => {
                        store.setLocationID(selectedOptions ? selectedOptions.map(opt => opt.value) : []);
                    }
                    }
                    styles={{
                        menu: (base) => ({
                            ...base,
                            zIndex: 9999,
                        }),
                        menuPortal: base => ({ ...base, zIndex: 1060 }),
                    }}
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
            <SuccessModal
                show={showSuccessModal}
                onHide={() => setShowSuccessModal(false)}
                fileName={store.spreadsheetFileName}
                submissionId={submissionId}
                lastEdited={lastEditedDate}
            />
            <FailureModal
                show={showFailureModal}
                onHide={() => setShowFailureModal(false)}
                errorMessage={store.submissionErrors}
            />
        </div>
    );
});