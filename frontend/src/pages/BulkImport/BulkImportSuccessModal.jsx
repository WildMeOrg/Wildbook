import React from "react";
import { Modal, Button, Card } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useNavigate } from "react-router-dom";
import { ArrowRight, ArrowLeft, Download } from "react-bootstrap-icons";

const SuccessModal = ({ show, onHide, fileName, submissionId, lastEdited }) => {
  const navigate = useNavigate();

  const goToTaskDetails = () => {
    navigate(`/bulk-import-task?id=${submissionId}`);
                    // window.location.href = `/bulk-import-task?id=${result.bulkImportId}`;

  };

  const goToHome = () => {
    navigate("/");
  };

  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="BULK_IMPORT_SUCCESS" defaultMessage="Bulk Import Started Successfully" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p>
          <FormattedMessage
            id="BULK_IMPORT_SUCCESS_DESC"
            defaultMessage="Your submission was successful and is now being processed in the background. You can track the progress of this task from the bulk import task details page."
          />
        </p>

        <Card className="mt-3 p-3 d-flex flex-row align-items-center justify-content-between">
          <div className="d-flex align-items-center">
            <ArrowLeft size={24} className="me-3" />
            <Download size={24} className="me-2 text-primary" />
            <div>
              <div className="fw-bold">{fileName}</div>
              <div>Identification in Progress</div>
              <div className="text-muted">Last Edited: {lastEdited}</div>
            </div>
          </div>
          <Button variant="outline-primary" onClick={goToTaskDetails}>
            <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
            <ArrowRight className="ms-1" />
          </Button>
        </Card>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="primary" onClick={goToTaskDetails}>
          <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
        </Button>
        <Button variant="outline-primary" onClick={goToHome}>
          <FormattedMessage id="GO_HOME" defaultMessage="Go to Home" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default SuccessModal;
