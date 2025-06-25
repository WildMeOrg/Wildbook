import React, { useEffect, useState } from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { FaDownload } from "react-icons/fa";

export const BulkImportContinueModal = ({ store, setRenderMode1 }) => {
  const [show, setShow] = useState(Boolean(store.submissionId));

  const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
  const uploadedImageCount = savedStore?.uploadedImages?.length || 0;
  const lastSavedAt = savedStore.lastSavedAt || new Date.now();

  useEffect(() => {
    const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
    if (savedStore?.submissionId) {
      setShow(true);
    }
  }, []);

  const handleContinue = () => {
    const savedStore = JSON.parse(localStorage.getItem("BulkImportStore"));
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
    <Modal size="lg" show={show} onHide={() => setShow(false)} centered>
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
            className="d-flex align-items-center justify-content-center rounded-circle bg-light me-3"
            style={{ width: 42, height: 42 }}
          >
            <FaDownload className="text-info" />
          </div>

          <div className="flex-grow-1">
            <div className="fw-semibold">{store.worksheetInfo.fileName}</div>
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

          <Button
            variant="outline-info"
            size="sm"
            className="px-3"
            onClick={() => store.openDraftDetail?.()}
          >
            <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
          </Button>
        </div>
      </Modal.Body>

      <Modal.Footer>
        <Button variant="primary" onClick={handleContinue}>
          <FormattedMessage id="CONTINUE" defaultMessage="Resume" />
        </Button>
        <Button variant="secondary" onClick={handleDelete}>
          <FormattedMessage
            id="BULK_IMPORT_START_NEW_IMPORT"
            defaultMessage="Start New Import"
          />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default BulkImportContinueModal;
