import React from "react";
import { Modal, Form, Button } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

const CreateNewIndividualModal = ({
  show,
  onHide,
  encounterId,
  newIndividualName,
  onNameChange,
  onConfirm,
  loading,
  themeColor,
  identificationRemarks = [],
}) => {
  const suggestedId = Math.floor(Math.random() * 90000) + 10000;
  const [selectedRemark, setSelectedRemark] = React.useState("");

  React.useEffect(() => {
    if (!show) {
      setSelectedRemark("");
    }
  }, [show]);

  const handleConfirm = () => {
    onConfirm(selectedRemark);
  };

  return (
    <Modal show={show} onHide={onHide} centered>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage
            id="CREATE_NEW_INDIVIDUAL"
            defaultMessage="Create New Individual"
          />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body>
        <p className="mb-3">
          <FormattedMessage
            id="CREATE_NEW_INDIVIDUAL_DESCRIPTION"
            defaultMessage="Create a new individual and assign a new name for Encounter"
          />{" "}
          <a
            href={`/react/encounter?number=${encodeURIComponent(encounterId)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {encounterId}
          </a>
        </p>

        <Form>
          <Form.Group className="mb-3">
            <Form.Label>
              <FormattedMessage id="MATCHED_BY" defaultMessage="Matched by" />
            </Form.Label>
            <Form.Select
              value={selectedRemark}
              onChange={(e) => setSelectedRemark(e.target.value)}
            >
              <option value="">
                <FormattedMessage
                  id="SELECT_MATCH_METHOD"
                  defaultMessage="Select match method"
                />
              </option>
              {identificationRemarks.map((remark, index) => (
                <option key={index} value={remark}>
                  {remark}
                </option>
              ))}
            </Form.Select>
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>
              <FormattedMessage
                id="NEW_INDIVIDUAL_ID"
                defaultMessage="New Individual ID"
              />
            </Form.Label>
            <Form.Control
              type="text"
              value={newIndividualName}
              onChange={(e) => onNameChange(e.target.value)}
              placeholder="Enter name"
            />
          </Form.Group>

          <div className="mb-3">
            <span className="text-muted">
              <FormattedMessage
                id="SUGGESTED_ID"
                defaultMessage="Suggested ID"
              />
              : {suggestedId}
            </span>{" "}
            <Button
              variant="link"
              size="sm"
              style={{ color: themeColor.primaryColors.primary500 }}
              onClick={() => onNameChange(suggestedId.toString())}
            >
              <FormattedMessage id="USE_THIS" defaultMessage="Use This" />
            </Button>
          </div>

          {/* <Form.Group className="mb-3">
            <Form.Label>
              <FormattedMessage id="ALTERNATE_ID" defaultMessage="Alternate ID" />
            </Form.Label>
            <Form.Control
              type="text"
              placeholder="individual name"
            />
          </Form.Group> */}
        </Form>
      </Modal.Body>

      <Modal.Footer>
        <Button
          style={{
            backgroundColor: themeColor.primaryColors.primary500,
            border: "none",
            color: "white",
          }}
          onClick={handleConfirm}
          disabled={!newIndividualName.trim() || loading}
        >
          <FormattedMessage
            id="CREATE_NEW_INDIVIDUAL"
            defaultMessage="Create New Individual"
          />
        </Button>
        <Button
          variant="outline-primary"
          style={{
            color: themeColor.primaryColors.primary500,
            borderColor: themeColor.primaryColors.primary500,
          }}
          onClick={onHide}
        >
          <FormattedMessage id="CANCEL" />
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default CreateNewIndividualModal;
