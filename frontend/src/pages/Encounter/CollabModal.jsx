import React, { useState } from "react";
import { Container, Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";

export default function CollabModal({ store }) {
  const collabStatus = store.encounterData?.collaborationState || "none";
  const dataOwner = store.encounterData?.assignedUsername || "admin123";
  const [collabMessage, setCollabMessage] = useState("");
  const [showRequestModal, setShowRequestModal] = useState(
    collabStatus === "none",
  );

  return (
    <Container style={{ padding: "20px" }}>
      <h2>
        <FormattedMessage id="ACCESS_DENIED" />
      </h2>
      <p>
        <FormattedMessage id="NO_ACCESS_TO_ENCOUNTER" />
      </p>

      <Modal
        show={showRequestModal && collabStatus === "none"}
        onHide={() => {
          setShowRequestModal(false);
          window.location.href = "/react";
        }}
      >
        <Modal.Header closeButton>
          <Modal.Title style={{ fontSize: "22px" }}>
            <FormattedMessage id="ACCESS_LIMITED" />
          </Modal.Title>
        </Modal.Header>

        <Modal.Body style={{ textAlign: "center" }}>
          <h4 style={{ marginBottom: "20px" }}>
            <FormattedMessage id="REQUEST_COLLAB_WITH" />{" "}
            <span
              style={{
                border: "1px solid #ccc",
                padding: "4px 8px",
                borderRadius: "4px",
                background: "#f5f5f5",
              }}
            >
              {dataOwner}
            </span>
            ?
          </h4>

          <p style={{ marginBottom: "20px" }}>
            <FormattedMessage id="REQUEST_COLLAB_MESSAGE" />
          </p>

          <textarea
            placeholder="optional message"
            style={{
              width: "100%",
              minHeight: "90px",
              padding: "10px",
              borderRadius: "6px",
              border: "1px solid #ccc",
            }}
            onChange={(e) => setCollabMessage(e.target.value)}
          />

          <div
            style={{
              marginTop: "25px",
              display: "flex",
              justifyContent: "center",
              gap: "20px",
            }}
          >
            <button
              className="btn btn-primary"
              onClick={() => {
                store.requestCollaboration({
                  message: collabMessage,
                });
                setShowRequestModal(false);
              }}
            >
              <FormattedMessage id="SUBMIT" />
            </button>

            <button
              className="btn btn-secondary"
              onClick={() => {
                window.location.href = "/react";
              }}
            >
              <FormattedMessage id="CANCEL" />
            </button>
          </div>
        </Modal.Body>
      </Modal>
      <Modal
        show={collabStatus === "rejected"}
        onHide={() => {
          window.location.href = "/react";
        }}
      >
        <Modal.Header closeButton>
          <Modal.Title style={{ fontSize: "22px" }}>
            <FormattedMessage id="ACCESS_LIMITED" />
          </Modal.Title>
        </Modal.Header>

        <Modal.Body style={{ textAlign: "center" }}>
          <p style={{ marginBottom: "20px" }}>
            <FormattedMessage id="REQUEST_REJECTED" />
          </p>
        </Modal.Body>
      </Modal>
      <Modal
        size="lg"
        show={collabStatus === "initialized"}
        onHide={() => {
          window.location.href = "/react";
        }}
      >
        <Modal.Header closeButton>
          <Modal.Title style={{ fontSize: "22px" }}>
            <FormattedMessage id="ACCESS_LIMITED" />
          </Modal.Title>
        </Modal.Header>

        <Modal.Body style={{ textAlign: "center" }}>
          <p style={{ marginBottom: "20px" }}>
            <FormattedMessage id="ACCESS_PENDING" />
          </p>
        </Modal.Body>
      </Modal>
    </Container>
  );
}
