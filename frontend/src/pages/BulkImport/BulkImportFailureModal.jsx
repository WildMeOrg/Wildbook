import React from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { ExclamationTriangleFill } from "react-bootstrap-icons";
import { useNavigate } from "react-router-dom";

const FailureModal = ({ show, onHide, errorMessage }) => {
  const navigate = useNavigate();

  const goToHome = () => {
    navigate("/");
  };

  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title className="text-danger d-flex align-items-center">
          <ExclamationTriangleFill className="me-2" />
          <FormattedMessage id="BULK_IMPORT_FAILED" defaultMessage="Bulk Import Failed" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p>
          <FormattedMessage
            id="BULK_IMPORT_FAILED_DESC"
            defaultMessage="There was an error while processing your submission. Please try again later or contact support."
          />
        </p>
        {errorMessage && (
          <div className="alert alert-danger mt-3">
            <strong>Error:</strong> {"error "}
          </div>
        )}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="outline-secondary" onClick={onHide}>
          <FormattedMessage id="CLOSE" defaultMessage="Close" />
        </Button>
        <Button variant="primary" onClick={goToHome}>
          <FormattedMessage id="GO_HOME" defaultMessage="Go to Home" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default FailureModal;
