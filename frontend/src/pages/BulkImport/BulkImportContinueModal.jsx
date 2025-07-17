import React, { useEffect, useState, useContext } from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import MainButton from "../../components/MainButton";
import ThemeContext from "../../ThemeColorProvider";

export const BulkImportContinueModal = ({ store, setRenderMode1 }) => {
  const [show, setShow] = useState(Boolean(store.submissionId));
  const theme = useContext(ThemeContext);

  const savedStore = React.useMemo(() => {
    return JSON.parse(localStorage.getItem("BulkImportStore") || "{}");
  }, []);

  const uploadedImageCount = savedStore?.uploadedImages?.length || 0;
  const lastSavedAt = savedStore?.lastSavedAt || Date.now();

  useEffect(() => {
    if (savedStore?.submissionId) {
      setShow(true);
    }
  }, []);

  const handleContinue = () => {
    const submissionId = savedStore?.submissionId;
    if (submissionId) {
      setRenderMode1("list");
      store.hydrate(savedStore);
      store.setActiveStep(0);
      store.fetchAndApplyUploaded();
    }
    setShow(false);
  };

  const handleDelete = () => {
    store.resetToDefaults();
    localStorage.removeItem("BulkImportStore");
    window.location.reload();
  };

  return (
    <Modal
      size="lg"
      show={show}
      onHide={() => setShow(false)}
      centered
      id="bulk-import-continue-modal"
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage
            id="BULK_IMPORT_CONTINUE_MODAL_TITLE"
            defaultMessage="Resume Bulk Import"
          />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <p className="mb-4">
          <FormattedMessage
            id="BULK_IMPORT_CONTINUE_MODAL_DESC"
            defaultMessage="You have an unfinished bulk report saved as a draft. Would you like to continue working on it or create a new bulk report? Please note that starting a new report will erase all your current progress and any data uploaded for this bulk import."
          />
        </p>

        <div className="d-flex align-items-center p-3 border rounded">
          <div
            className="d-flex align-items-center justify-content-center rounded-circle me-3"
            style={{
              width: 42,
              height: 42,
              backgroundColor: theme.primaryColors.primary100,
            }}
          >
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M19 9.5H15V3.5H9V9.5H5L12 16.5L19 9.5ZM5 18.5V20.5H19V18.5H5Z"
                fill={theme.primaryColors.primary500}
              />
            </svg>
          </div>
          <div className="flex-grow-1">
            <div className="fw-semibold">{savedStore.spreadsheetFileName}</div>
            <div className="small text-muted">
              {uploadedImageCount}{" "}
              <FormattedMessage
                id="IMAGES_UPLOADED"
                defaultMessage="Images uploaded"
              />
            </div>
            <div className="small text-muted">
              <FormattedMessage id="LAST_EDITED" defaultMessage="Last Edited" />
              : {new Date(lastSavedAt).toLocaleString()}
            </div>
          </div>

          {/* <Button
            variant="outline-info"
            size="sm"
            className="px-3"
            onClick={() => store.openDraftDetail?.()}
          >
            <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
          </Button> */}
        </div>
      </Modal.Body>

      <Modal.Footer>
        <MainButton
          onClick={handleContinue}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="CONTINUE" defaultMessage="Resume" />
        </MainButton>
        <MainButton
          onClick={handleDelete}
          borderColor={theme.primaryColors.primary500}
          color={theme.primaryColors.primary500}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage
            id="BULK_IMPORT_START_NEW_IMPORT"
            defaultMessage="Start New Import"
          />
        </MainButton>
      </Modal.Footer>
    </Modal>
  );
};

export default BulkImportContinueModal;
