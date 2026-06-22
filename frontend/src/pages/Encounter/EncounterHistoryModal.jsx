import React from "react";
import { Modal } from "react-bootstrap";
import DOMPurify from "dompurify";
import "./styles.css";
import { FormattedMessage } from "react-intl";

export default function EncounterHistoryModal({
  store = {},
  isOpen = false,
  onClose = () => {},
}) {
  if (!isOpen) return null;
  const raw = store.encounterData?.researcherComments || "";

  const safe = DOMPurify.sanitize(raw, {
    ALLOWED_TAGS: ["p", "em", "i", "br", "a"],
    ALLOWED_ATTR: ["href", "title", "target", "data-annot-id"],
  });
  return (
    <Modal
      size="lg"
      show={isOpen}
      onHide={onClose}
      backdrop
      keyboard
      scrollable
      centered
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="ENCOUNTER_HISTORY" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        {raw ? (
          <div
            className="encounter-history"
            dangerouslySetInnerHTML={{ __html: safe }}
          />
        ) : (
          <em>
            <FormattedMessage id="NO_RECORD" />
          </em>
        )}
      </Modal.Body>
    </Modal>
  );
}
