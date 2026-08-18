import React from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

const MatchConfirmedModal = ({
  show,
  onHide,
  encounterId,
  encounterCount,
  individualId,
  individualName,
  themeColor,
}) => {
  const handleClose = () => {
    onHide();
    window.location.reload();
  };

  return (
    <Modal
      show={show}
      onHide={handleClose}
      centered
      id="match-confirmed-modal"
      data-testid="match-confirmed-modal"
    >
      <Modal.Header
        closeButton
        id="match-confirmed-modal-header"
        data-testid="match-confirmed-modal-header"
      >
        <Modal.Title
          id="match-confirmed-modal-title"
          data-testid="match-confirmed-modal-title"
        >
          <FormattedMessage
            id="MATCH_CONFIRMED"
            defaultMessage="Match Confirmed!"
          />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body
        id="match-confirmed-modal-body"
        data-testid="match-confirmed-modal-body"
      >
        <p id="match-confirmed-message" data-testid="match-confirmed-message">
          {encounterCount > 0 ? (
            <>
              <FormattedMessage
                id="YOU_MERGED_N_ENCOUNTERS"
                defaultMessage="You merged {count} encounter(s) to Individual"
                values={{ count: encounterCount }}
              />{" "}
            </>
          ) : (
            <>
              <FormattedMessage
                id="YOU_MATCHED_ENCOUNTER"
                defaultMessage="You matched Encounter"
              />{" "}
              <a
                id="match-confirmed-encounter-link"
                data-testid="match-confirmed-encounter-link"
                href={`/react/encounter?number=${encodeURIComponent(encounterId)}`}
                target="_blank"
                rel="noopener noreferrer"
              >
                {encounterId}
              </a>{" "}
              <FormattedMessage
                id="WITH_INDIVIDUAL"
                defaultMessage="with Individual"
              />{" "}
            </>
          )}
          <a
            id="match-confirmed-individual-link"
            data-testid="match-confirmed-individual-link"
            href={`/individuals.jsp?id=${encodeURIComponent(individualId)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {individualName || individualId}
          </a>
        </p>
      </Modal.Body>

      <Modal.Footer
        id="match-confirmed-modal-footer"
        data-testid="match-confirmed-modal-footer"
      >
        <Button
          id="match-confirmed-close"
          data-testid="match-confirmed-close"
          variant="outline-primary"
          style={{
            color: themeColor.primaryColors.primary500,
            borderColor: themeColor.primaryColors.primary500,
          }}
          onClick={handleClose}
        >
          <FormattedMessage id="CLOSE" defaultMessage="Close" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default MatchConfirmedModal;
