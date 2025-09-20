import React from "react";
import { Modal, Card } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { useNavigate } from "react-router-dom";
import MainButton from "../../components/MainButton";
import ThemeContext from "../../ThemeColorProvider";

const SuccessModal = ({ show, onHide, fileName, submissionId, lastEdited }) => {
  const navigate = useNavigate();
  const theme = React.useContext(ThemeContext);
  const goToTaskDetails = () => {
    onHide();
    window.location.href = `${process.env.PUBLIC_URL}/bulk-import-task?id=${submissionId}`;
  };

  const goToHome = () => {
    onHide();
    navigate("/");
  };

  return (
    <Modal
      size="lg"
      show={show}
      onHide={onHide}
      centered
      id="bulk-import-success-modal"
    >
      <Modal.Header>
        <Modal.Title>
          <FormattedMessage
            id="BULK_IMPORT_SUCCESS"
            defaultMessage="Bulk Import Started Successfully"
          />
        </Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <p>
          <FormattedMessage
            id="BULK_IMPORT_SUCCESS_DESC"
            defaultMessage="Your submission was successful and is now being processed in the background. You can track the progress of this task from the bulk import task details page."
          />
        </p>

        <Card className="mt-3 p-3 d-flex flex-row align-items-center">
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
            <div className="text-muted">
              <FormattedMessage id="BULK_IMPORT_STARTED_AT" /> {lastEdited}
            </div>
          </div>
        </Card>
      </Modal.Body>
      <Modal.Footer>
        <MainButton
          onClick={goToTaskDetails}
          backgroundColor={theme.wildMeColors.cyan700}
          color={theme.defaultColors.white}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="SEE_DETAILS" defaultMessage="See Details" />
        </MainButton>
        <MainButton
          onClick={goToHome}
          borderColor={theme.primaryColors.primary500}
          color={theme.primaryColors.primary500}
          noArrow={true}
          style={{ width: "auto", fontSize: "1rem" }}
        >
          <FormattedMessage id="GO_HOME" defaultMessage="Go to Home" />
        </MainButton>
      </Modal.Footer>
    </Modal>
  );
};

export default SuccessModal;
