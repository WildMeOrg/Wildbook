import React, { useState } from "react";
import { Modal, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useNavigate } from "react-router-dom";
import { FaDownload } from "react-icons/fa";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";

export const BulkImportUnfinishedTaskModal = ({ taskId, fileName, dateCreated, taskStatus }) => {
  const navigate = useNavigate();
  const [show, setShow] = useState(true);
  const theme = React.useContext(ThemeContext);

  const handleContinue = () => {
    setShow(false);
    navigate("/bulk-import-task?id=" + taskId);
  };

  return (
    <Modal show={show} onHide={() => setShow(false)} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage
            id="BULK_IMPORT_CONTINUE_TITLE"
            defaultMessage="Start another Bulk Import?"
          />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p className="mb-3">
          <FormattedMessage
            id="BULK_IMPORT_CONTINUE_DESC"
            defaultMessage="Your last bulk import is still being processed in the background. You can check its progress from the bulk import task page. Would you like to start another import?"
          />
        </p>

        <div className="d-flex align-items-center border rounded p-3 mb-3">
          <div
            className="d-flex align-items-center justify-content-center rounded-circle bg-light me-3"
            style={{ width: 48, height: 48 }}
          >
            <FaDownload size={20} color={theme.primaryColors.primary500} />
          </div>
          <div>
            <div className="fw-bold">{fileName}</div>
            <div className="text-muted small">{taskStatus}</div>
            <div className="text-muted small">{dateCreated}</div>

          </div>
        </div>

        <div className="d-flex justify-content-end">
          <MainButton
            onClick={handleContinue}
            backgroundColor={theme.primaryColors.primary500}
            color={theme.defaultColors.white}
            noArrow={true}
            style={{ width: "auto", fontSize: "1rem" }}
          >
            <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
          </MainButton>
          <MainButton
            onClick={() => setShow(false)}
            borderColor={theme.primaryColors.primary500}
            color={theme.primaryColors.primary500}
            noArrow={true}
            style={{ width: "auto", fontSize: "1rem" }}
          >
            <FormattedMessage id="START_NEW_BULK_IMPORT" defaultMessage="Start New Bulk Import" />
          </MainButton>


        </div>
      </Modal.Body>
    </Modal>
  );
};
