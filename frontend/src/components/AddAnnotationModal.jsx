import React from "react";
import { Button, Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";

export default function AddAnnotationModal({
  showModal,
  setShowModal,
  incomplete,
  error,
}) {
  const intl = useIntl();

  return (
    <Modal show={showModal} onHide={() => setShowModal(false)}>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="SUBMISSION_FAILED" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {incomplete && intl.formatMessage({ id: "MISSING_REQUIRED_FIELDS" })}
        {error &&
          error.slice().map((error, index) => {
            return (
              <div key={index} className="d-flex flex-column">
                {error.code === "INVALID" && (
                  <p>
                    <FormattedMessage id="BEERROR_INVALID" />
                    {error.fieldName}{" "}
                  </p>
                )}
                {error.code === "REQUIRED" && (
                  <p>
                    <FormattedMessage id="BEERROR_REQUIRED" />
                    {error.fieldName}{" "}
                  </p>
                )}
                {!error.code && (
                  <p>
                    <FormattedMessage id="BEERROR_UNKNOWN" />
                    {error.fieldName}{" "}
                  </p>
                )}
              </div>
            );
          })}
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={() => setShowModal(false)}>
          <FormattedMessage id="SESSION_CLOSE" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
}
