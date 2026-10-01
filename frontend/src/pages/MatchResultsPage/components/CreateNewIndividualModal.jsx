import React from "react";
import { Modal, Form, Button, Spinner } from "react-bootstrap";
import { FormattedMessage, useIntl } from "react-intl";
import { toast } from "react-toastify";

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
  const intl = useIntl();
  const [selectedRemark, setSelectedRemark] = React.useState("");
  const [suggestion, setSuggestion] = React.useState(null);
  const [loadingSuggestedId, setLoadingSuggestedId] = React.useState(false);

  const suggestedId = suggestion?.nextName;

  React.useEffect(() => {
    setSuggestion(null);
    setLoadingSuggestedId(false);
    if (!show) return undefined;

    const controller = new AbortController();
    setLoadingSuggestedId(true);

    const url = "/api/v3/individuals/info/next_name";
    fetch(
      locationId ? `${url}?locationId=${encodeURIComponent(locationId)}` : url,
      { signal: controller.signal },
    )
      .then(async (res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then((data) => {
        if (controller.signal.aborted) return;
        const result =
          data.success === true && Array.isArray(data.results)
            ? data.results.find(
                (r) =>
                  r.success === true &&
                  typeof r.nextName === "string" &&
                  r.nextName.trim() &&
                  ["locationId", "user"].includes(r.type),
              )
            : null;
        setSuggestion(result || null);
      })
      .catch((err) => {
        if (controller.signal.aborted || err.name === "AbortError") return;

        setSuggestion(null);
        toast.error(
          intl.formatMessage({
            id: "LOAD_SUGGESTED_ID_FAILED",
            defaultMessage: "Failed to load suggested ID",
          }),
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoadingSuggestedId(false);
        }
      });

    return () => {
      controller.abort();
    };
  }, [show, locationId, intl]);

  React.useEffect(() => {
    if (!show) {
      setSelectedRemark("");
      setSuggestion(null);
    }
  }, [show]);

  const handleConfirm = () => {
    onConfirm(selectedRemark);
  };

  const handleUseSuggestedId = () => {
    onNameChange(suggestedId, suggestion.type === "locationId");
    toast.success(
      intl.formatMessage({
        id: "SUGGESTED_ID_APPLIED",
        defaultMessage: "Suggested ID applied",
      }),
    );
  };

  return (
    <Modal
      show={show}
      onHide={onHide}
      centered
      id="create-new-individual-modal"
      data-testid="create-new-individual-modal"
    >
      <Modal.Header
        closeButton
        id="create-new-individual-modal-header"
        data-testid="create-new-individual-modal-header"
      >
        <Modal.Title
          id="create-new-individual-modal-title"
          data-testid="create-new-individual-modal-title"
        >
          <FormattedMessage
            id="CREATE_NEW_INDIVIDUAL"
            defaultMessage="Create New Individual"
          />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body
        id="create-new-individual-modal-body"
        data-testid="create-new-individual-modal-body"
      >
        <p
          className="mb-3"
          id="create-new-individual-description"
          data-testid="create-new-individual-description"
        >
          <FormattedMessage
            id="CREATE_NEW_INDIVIDUAL_DESCRIPTION"
            defaultMessage="Create a new individual and assign a new name for Encounter"
          />{" "}
          <a
            id="create-new-individual-encounter-link"
            data-testid="create-new-individual-encounter-link"
            href={`/react/encounter?number=${encodeURIComponent(encounterId)}`}
            target="_blank"
            rel="noopener noreferrer"
          >
            {encounterId}
          </a>
        </p>

        <Form
          id="create-new-individual-form"
          data-testid="create-new-individual-form"
        >
          <Form.Group
            className="mb-3"
            id="create-new-individual-remark-group"
            data-testid="create-new-individual-remark-group"
          >
            <Form.Label
              id="create-new-individual-remark-label"
              data-testid="create-new-individual-remark-label"
            >
              <FormattedMessage id="MATCHED_BY" defaultMessage="Matched by" />
            </Form.Label>

            <Form.Select
              id="create-new-individual-remark-select"
              data-testid="create-new-individual-remark-select"
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
                <option
                  key={index}
                  value={remark}
                  data-testid={`create-new-individual-remark-option-${index}`}
                >
                  {remark}
                </option>
              ))}
            </Form.Select>
          </Form.Group>

          <Form.Group
            className="mb-3"
            id="create-new-individual-id-group"
            data-testid="create-new-individual-id-group"
          >
            <Form.Label
              id="create-new-individual-id-label"
              data-testid="create-new-individual-id-label"
            >
              <FormattedMessage
                id="NEW_INDIVIDUAL_ID"
                defaultMessage="New Individual ID"
              />
            </Form.Label>

            <Form.Control
              id="create-new-individual-id-input"
              data-testid="create-new-individual-id-input"
              type="text"
              value={newIndividualName}
              onChange={(e) => onNameChange(e.target.value, false)}
              placeholder="Enter name"
            />
          </Form.Group>

          {(loadingSuggestedId || suggestedId) && (
            <div
              className="mb-3"
              id="create-new-individual-suggested-id"
              data-testid="create-new-individual-suggested-id"
            >
              {loadingSuggestedId ? (
                <span
                  className="text-muted"
                  id="create-new-individual-suggested-id-loading"
                  data-testid="create-new-individual-suggested-id-loading"
                >
                  <Spinner
                    animation="border"
                    size="sm"
                    className="me-2"
                    data-testid="create-new-individual-suggested-id-spinner"
                  />
                  <FormattedMessage
                    id="LOADING_SUGGESTED_ID"
                    defaultMessage="Loading suggested ID..."
                  />
                </span>
              ) : suggestedId !== null && suggestedId !== undefined ? (
                <>
                  <span
                    className="text-muted"
                    id="create-new-individual-suggested-id-value"
                    data-testid="create-new-individual-suggested-id-value"
                  >
                    <FormattedMessage
                      id="SUGGESTED_ID"
                      defaultMessage="Suggested ID"
                    />
                    : {suggestedId}
                  </span>{" "}
                  <Button
                    id="create-new-individual-use-suggested-id"
                    data-testid="create-new-individual-use-suggested-id"
                    variant="link"
                    size="sm"
                    style={{ color: themeColor.primaryColors.primary500 }}
                    onClick={handleUseSuggestedId}
                  >
                    <FormattedMessage id="USE_THIS" defaultMessage="Use This" />
                  </Button>
                </>
              ) : null}
            </div>
          )}
        </Form>
      </Modal.Body>

      <Modal.Footer
        id="create-new-individual-modal-footer"
        data-testid="create-new-individual-modal-footer"
      >
        <Button
          id="create-new-individual-confirm"
          data-testid="create-new-individual-confirm"
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
          id="create-new-individual-cancel"
          data-testid="create-new-individual-cancel"
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
