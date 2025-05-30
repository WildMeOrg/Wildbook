//a modal to show unfinished tasks in bulk import
import React, { useState } from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useNavigate } from "react-router-dom";

export const BulkImportUnfinishedTaskModal = ({ taskId }) => {
  const navigate = useNavigate();

  const [show, setShow] = useState(true);
  const handleClose = () => setShow(false);

  const handleContinue = () => {
    setShow(false);
    navigate("/bulk-import-task?id=" + taskId);
  };

  return (
    <Modal show={show} onHide={handleClose} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage
            id="BULK_IMPORT_UNFINISHED_TASK_TITLE"
            defaultMessage="Unfinished Bulk Import Task"
          />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <>
          <p>
            <FormattedMessage
              id="BULK_IMPORT_UNFINISHED_TASK_DESC"
              defaultMessage="You have an unfinished bulk import task. Would you like to continue?"
            />
          </p>

          <div>
            <strong>
              <FormattedMessage
                id="BULK_IMPORT_TASK_ID"
                defaultMessage="Task ID:"
              />
            </strong>{" "}
            {taskId}
          </div>
        </>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="secondary" onClick={handleClose}>
          <FormattedMessage id="CANCEL" defaultMessage="Cancel" />
        </Button>
        <Button variant="primary" onClick={handleContinue}>
          <FormattedMessage id="CONTINUE" defaultMessage="Continue" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};
