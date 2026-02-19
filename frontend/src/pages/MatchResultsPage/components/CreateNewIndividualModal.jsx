import React from "react";
import { Modal, Form, Button, Spinner } from "react-bootstrap";
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
  locationId = "",
}) => {
  const [selectedRemark, setSelectedRemark] = React.useState("");
  const [suggestedId, setSuggestedId] = React.useState(null);
  const [loadingSuggestedId, setLoadingSuggestedId] = React.useState(false);

  React.useEffect(() => {
    if (show && locationId) {
      setLoadingSuggestedId(true);
      fetch(
        `/api/v3/individuals/info/next_name?locationId=${encodeURIComponent(locationId)}`,
      )
        .then(async (res) => {
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return res.json();
        })
        .then((data) => {
          if (data.success && data.results && data.results.length > 0) {
            const successfulResult = data.results.find((r) => r.success);
            console.log("successfulResult", JSON.stringify(successfulResult));
            if (successfulResult && successfulResult.nextName) {
              setSuggestedId(successfulResult.nextName);
            } else {
              setSuggestedId(null);
            }
          } else {
            setSuggestedId(null);
          }
        })
        .catch((err) => {
          console.error("Failed to fetch suggested ID:", err);
          setSuggestedId(null);
        })
        .finally(() => {
          setLoadingSuggestedId(false);
        });
    }
  }, [show, locationId]);

  React.useEffect(() => {
    if (!show) {
      setSelectedRemark("");
      setSuggestedId(null);
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

          {locationId && (
            <div className="mb-3">
              {loadingSuggestedId ? (
                <span className="text-muted">
                  <Spinner animation="border" size="sm" className="me-2" />
                  <FormattedMessage
                    id="LOADING_SUGGESTED_ID"
                    defaultMessage="Loading suggested ID..."
                  />
                </span>
              ) : suggestedId ? (
                <>
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
                </>
              ) : null}
            </div>
          )}
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
