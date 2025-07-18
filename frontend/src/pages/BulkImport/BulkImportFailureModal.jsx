import React from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { ExclamationTriangleFill } from "react-bootstrap-icons";

const FailureModal = ({ show, onHide, errorMessage }) => {
  const goToHome = () => {
    window.location.href = "/";
  };

  return (
    <Modal show={show} onHide={onHide} centered id="bulk-import-failure-modal">
      <Modal.Header closeButton>
        <Modal.Title className="text-danger d-flex align-items-center">
          <ExclamationTriangleFill className="me-2" />
          <FormattedMessage
            id="BULK_IMPORT_FAILED"
            defaultMessage="Bulk Import Failed"
          />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p>
          <FormattedMessage
            id="BULK_IMPORT_FAILED_DESC"
            defaultMessage="There was an error while processing your submission."
          />
        </p>
        {typeof errorMessage === "string" && (
          <div className="alert alert-danger mt-3">
            <strong>Error:</strong> {errorMessage}
          </div>
        )}
        {Array.isArray(errorMessage) && (
          <div className="alert alert-danger mt-3">
            <FormattedMessage id="BULK_IMPORT_ERROR_MESSAGE" />
          </div>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button
          variant="outline-secondary"
          onClick={() => {
            onHide();
            // store.setActiveStep(2);
            // window.scrollTo(0, 0);
          }}
        >
          <FormattedMessage id="REVIEW_DATA" defaultMessage="Review Data" />
        </Button>
        <Button variant="primary" onClick={goToHome}>
          <FormattedMessage id="GO_HOME" defaultMessage="Go to Home" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default FailureModal;
