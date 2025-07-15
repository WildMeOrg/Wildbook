import React, { useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { useContext, useCallback } from "react";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";
import usePostBulkImport from "../../models/bulkImport/usePostBulkImport";
import { v4 as uuidv4 } from "uuid";
import { reaction } from "mobx";
import SuccessModal from "./BulkImportSuccessModal";
import { useState } from "react";
import dayjs from "dayjs";
import FailureModal from "./BulkImportFailureModal";
import { Suspense, lazy } from "react";

const TreeSelect = lazy(() => import("antd/es/tree-select"));

function findNodeByValue(treeData, value) {
  for (const node of treeData) {
    if (node.value === value) return node;
    if (node.children) {
      const found = findNodeByValue(node.children, value);
      if (found) return found;
    }
  }
  return null;
}

function getAllDescendantValues(node) {
  let res = [];
  if (node.children) {
    for (const child of node.children) {
      res.push(child.value);
      res = res.concat(getAllDescendantValues(child));
    }
  }
  return res;
}

export const BulkImportSetLocation = observer(({ store }) => {
  const theme = useContext(ThemeContext);
  const { submit, isLoading } = usePostBulkImport();
  const submissionId = store.submissionId || uuidv4();
  const [showSuccessModal, setShowSuccessModal] = useState(false);
  const [showFailureModal, setShowFailureModal] = useState(false);
  const [lastEditedDate, setLastEditedDate] = useState(
    dayjs().format("YYYY-MM-DD"),
  );

  useEffect(() => {
    const disposer = reaction(
      () => store.spreadsheetData.map((row) => row["Encounter.locationID"]),
      (locationIDs) => {
        const uniqueIDs = Array.from(
          new Set(locationIDs.filter((id) => id && id.length > 0)),
        );

        const allIDs = new Set();
        uniqueIDs.forEach((id) => {
          allIDs.add(id);
          const node = findNodeByValue(store.locationIDOptions, id);
          if (node) {
            getAllDescendantValues(node).forEach((childId) =>
              allIDs.add(childId),
            );
          }
        });

        store.setLocationID(Array.from(allIDs));
      },
      { fireImmediately: true },
    );
    return () => disposer();
  }, [store.locationIDOptions]);

  const handleChange = (checkedValues, _labelList, extra) => {
    const newSet = new Set(store.locationID);
    const { triggerValue, checked } = extra;

    const node = findNodeByValue(store.locationIDOptions, triggerValue);

    if (checked) {
      newSet.add(triggerValue);
      if (node?.children) {
        getAllDescendantValues(node).forEach((v) => newSet.add(v));
      }
    } else {
      newSet.delete(triggerValue);
      if (node?.children) {
        getAllDescendantValues(node).forEach((v) => newSet.delete(v));
      }
    }
    store.setLocationID(Array.from(newSet));
  };

  const handleStartImport = useCallback(async () => {
    store.updateRawFromNormalizedRow();
    store.clearSubmissionErrors();
    try {
      const result = await submit(
        submissionId,
        store.rawColumns,
        store.rawData,
        store.spreadsheetFileName,
        store.locationID,
        store.skipDetection,
        store.skipIdentification,
      );
      if (result?.status === 200) {
        localStorage.removeItem("BulkImportStore");
        localStorage.setItem(
          "lastBulkImportTask",
          result?.data?.bulkImportId || submissionId,
        );
        setLastEditedDate(dayjs().format("YYYY-MM-DD"));
        setShowSuccessModal(true);
      } else if (result?.status === 400) {
        console.error(
          "Bulk import failed with status 400:",
          result.data.errors,
        );
        store.setSubmissionErrors(result.data.errors || "Unknown error");

        setShowFailureModal(true);
      } else {
        console.error("Bulk import failed with unexpected status:", result);
        store.setSubmissionErrors("Unexpected error during import");
        setShowFailureModal(true);
      }
    } catch (err) {
      const errors = err.response?.data?.errors;
      if (errors) {
        store.setSubmissionErrors(errors);
      } else {
        store.setSubmissionErrors("An unexpected error occurred during import");
      }
      setShowFailureModal(true);
    }
  });

  return (
    <div className="d-flex flex-column mt-4" id="bulk-import-set-location">
      <h5>
        <FormattedMessage id="BULK_IMPORT_SET_LOCATION" />
      </h5>
      <p>
        <FormattedMessage id="BULK_IMPORT_SET_LOCATION_DESC" />
      </p>
      <div
        style={{
          width: "500px",
          maxWidth: "100%",
        }}
      >
        <Suspense fallback={<div>Loading location picker...</div>}>
          <TreeSelect
            treeData={store.locationIDOptions}
            value={store.locationID}
            treeCheckable
            treeCheckStrictly
            showCheckedStrategy="SHOW_ALL"
            treeNodeFilterProp="value"
            treeLine
            showSearch
            size="large"
            allowClear
            style={{ width: "100%" }}
            placeholder="Select locations"
            dropdownStyle={{ maxHeight: "500px", zIndex: 9999 }}
            onChange={handleChange}
          />
        </Suspense>
      </div>

      <div className="d-flex flex-row justify-content-between mt-4">
        <MainButton
          onClick={() => {
            store.setActiveStep(2);
          }}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{
            width: "auto",
            fontSize: "1rem",
            marginTop: "auto",
          }}
        >
          {isLoading ? (
            <FormattedMessage id="LOADING" defaultMessage="Loading..." />
          ) : (
            <FormattedMessage id="PREVIOUS" />
          )}
        </MainButton>
        <div
          className="d-flex flex-column "
          style={{ width: "300px", marginLeft: "auto" }}
        >
          <div className="form-check mt-3">
            <input
              className="form-check-input"
              type="checkbox"
              id="skipDetection"
              checked={store.skipDetection}
              onChange={(e) => store.setSkipDetection(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="skipDetection">
              <FormattedMessage
                id="SKIP_DETECTION_AND_ID"
                defaultMessage="Skip Detection and Identification"
              />
            </label>
          </div>

          <div className="form-check mt-2">
            <input
              className="form-check-input"
              type="checkbox"
              id="skipDetectionAndID"
              checked={store.skipIdentification}
              onChange={(e) => store.setSkipIdentification(e.target.checked)}
            />
            <label className="form-check-label" htmlFor="skipDetectionAndID">
              <FormattedMessage
                id="SKIP_ONLY_IDENTIFICATION"
                defaultMessage="Skip only identification"
              />
            </label>
          </div>

          <MainButton
            onClick={() => {
              handleStartImport();
            }}
            disabled={
              isLoading ||
              store.spreadsheetUploadProgress !== 100 ||
              Object.keys(store.validationErrors).length > 0 ||
              store.missingRequiredColumns.length > 0
            }
            backgroundColor={theme.wildMeColors.cyan700}
            color={theme.defaultColors.white}
            noArrow={true}
            style={{
              width: "auto",
              fontSize: "1rem",
              marginRight: "auto",
            }}
          >
            {isLoading ? (
              <FormattedMessage id="LOADING" defaultMessage="Loading..." />
            ) : (
              <FormattedMessage id="START_BULK_IMPORT" />
            )}
          </MainButton>
        </div>
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
        onHide={() => {
          store.setActiveStep(2);
          window.scrollTo(0, document.body.scrollHeight);
          setShowFailureModal(false);
        }}
        errorMessage={store.submissionErrors}
      />
    </div>
  );
});
