import React from "react";
import { Modal, Button } from "react-bootstrap";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";

const ApplyToAllRowsModal = observer(({ store, columnId, newValue }) => {
  const handleConfirm = () => {
    store.applyToAllRows(columnId, newValue);
    store.setApplyToAllRowModalShow(false);
    store.invalidateValidation();
    const { errors, warnings } = store.validateSpreadsheet();
    store.setValidationErrors(errors);
    store.setValidationWarnings(warnings);
  };

  const handleCancel = () => {
    store.setApplyToAllRowModalShow(false);
  };

  return (
    <Modal
      size="lg"
      show={store.applyToAllRowModalShow}
      onHide={handleCancel}
      centered
    >
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="BULK_IMPORT_APPLY_TO_ALL_ROWS_TITLE" />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <FormattedMessage
          id="BULK_IMPORT_APPLY_TO_ALL_ROWS_BODY"
          values={{
            columnId: <strong>{columnId}</strong>,
            newValue: <strong>{newValue}</strong>,
          }}
        />
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleCancel}>
          <FormattedMessage id="BULK_IMPORT_CLOSE" />
        </Button>
        <Button variant="primary" onClick={handleConfirm}>
          <FormattedMessage id="BULK_IMPORT_APPLY_TO_ALL" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
});

export default ApplyToAllRowsModal;
