import React from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

const NewIndividualCreatedModal = ({
  show,
  onHide,
  encounterId,
  individualName,
  themeColor,
}) => {
  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="NEW_INDIVIDUAL_CREATED" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <p>
          <FormattedMessage id="ASSIGNED_ENCOUNTER_AS_NEW_INDIVIDUAL" />{" "}
          <a
            href={`/react/encounter?number=${encodeURIComponent(encounterId)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {encounterId}
          </a>{" "}
          <FormattedMessage id="AS_NEW_INDIVIDUAL" /> {individualName}.
        </p>
      </Modal.Body>

      <Modal.Footer>
        <Button
          variant="outline-primary"
          style={{
            color: themeColor.primaryColors.primary500,
            borderColor: themeColor.primaryColors.primary500,
          }}
          onClick={onHide}
        >
          <FormattedMessage id="CLOSE" defaultMessage="Close" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default NewIndividualCreatedModal;
