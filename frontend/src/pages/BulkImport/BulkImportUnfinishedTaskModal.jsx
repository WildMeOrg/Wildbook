import React, { useState } from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useNavigate } from "react-router-dom";
import ThemeContext from "../../ThemeColorProvider";
import MainButton from "../../components/MainButton";

export const BulkImportUnfinishedTaskModal = ({
  taskId,
  fileName,
  dateCreated,
  taskStatus,
}) => {
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
            className="d-flex align-items-center justify-content-center rounded-circle me-3"
            style={{
              width: 42,
              height: 42,
              backgroundColor: theme.primaryColors.primary100,
            }}
          >
            <svg
              width="24"
              height="24"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
            >
              <path
                d="M19 9.5H15V3.5H9V9.5H5L12 16.5L19 9.5ZM5 18.5V20.5H19V18.5H5Z"
                fill={theme.primaryColors.primary500}
              />
            </svg>
          </div>
          <div>
            <div className="fw-bold">{fileName}</div>
            <div className="text-muted small">{taskStatus}</div>
            <div className="text-muted small">
              {new Date(dateCreated).toLocaleString()}
            </div>
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
            onClick={() => {
              setShow(false);
              localStorage.removeItem("lastBulkImportTask");
            }}
            borderColor={theme.primaryColors.primary500}
            color={theme.primaryColors.primary500}
            noArrow={true}
            style={{ width: "auto", fontSize: "1rem" }}
          >
            <FormattedMessage
              id="START_NEW_BULK_IMPORT"
              defaultMessage="Start New Bulk Import"
            />
          </MainButton>
        </div>
      </Modal.Body>
    </Modal>
  );
};
