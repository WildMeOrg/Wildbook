import { Modal, Button } from "react-bootstrap";
import { observer } from "mobx-react-lite";
import React from "react";

const ApplyToAllRowsModal = observer(({ store, columnId, newValue }) => {
  const handleConfirm = () => {
    store.applyToAllRows(columnId, newValue);
    store.setApplyToAllRowModalShow(false);
    console.log("store.imageSectionFileNames", store.imageSectionFileNames.length);
    store.invalidateValidation();
    const { errors, warnings } = store.validateSpreadsheet();
    store.setValidationErrors(errors);
    store.setValidationWarnings(warnings);
  };

  const handleCancel = () => {
    store.setApplyToAllRowModalShow(false);
  };

  return (
    <Modal show={store.applyToAllRowModalShow} onHide={handleCancel} centered>
      <Modal.Header closeButton>
        <Modal.Title>Apply to All Rows</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        Do you want to apply this <strong>{columnId}</strong> value (
        <strong>{newValue}</strong>) to all rows?
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleCancel}>
          Close
        </Button>
        <Button variant="primary" onClick={handleConfirm}>
          Apply to All
        </Button>
      </Modal.Footer>
    </Modal>
  );
});

export default ApplyToAllRowsModal;
