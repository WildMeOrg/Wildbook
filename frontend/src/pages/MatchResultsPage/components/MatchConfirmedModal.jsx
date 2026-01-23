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
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage
            id="MATCH_CONFIRMED"
            defaultMessage="Match Confirmed!"
          />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <p>
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
            href={`/individuals.jsp?id=${encodeURIComponent(individualId)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {individualName || individualId}
          </a>
        </p>
      </Modal.Body>

      <Modal.Footer>
        <Button
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
