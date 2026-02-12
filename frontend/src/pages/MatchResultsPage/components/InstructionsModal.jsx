import React from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import ToggoleAnnotationIcon from "../icons/ToggoleAnnotationIcon";
import HatchMarkIcon from "../icons/HatchMarkIcon";
import FullScreenIcon from "../icons/FullScreenIcon";

const SectionTitle = ({ id }) => (
  <div style={{ fontWeight: 700, marginTop: 14, marginBottom: 6 }}>
    <FormattedMessage id={id} />
  </div>
);

const BulletList = ({ items }) => (
  <ul style={{ marginTop: 6, marginBottom: 6, paddingLeft: 20 }}>
    {items.map((id) => (
      <li key={id} style={{ marginBottom: 4 }}>
        <FormattedMessage id={id} />
      </li>
    ))}
  </ul>
);

export default function InstructionsModal({
  show,
  onHide,
  taskId,
  themeColor,
}) {
  const primary = themeColor?.primaryColors?.primary500 || "#0d6efd";
  const [copied, setCopied] = React.useState(false);

  const handleCopy = async () => {
    if (!taskId) return;
    try {
      await navigator.clipboard.writeText(taskId);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1200);
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <Modal show={show} size="xl" onHide={onHide} centered scrollable>
      <Modal.Header closeButton>
        <Modal.Title>
          <FormattedMessage id="MATCHING_PAGE_INSTRUCTIONS" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body style={{ lineHeight: 1.35 }}>
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            marginBottom: 10,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ color: "#333", fontWeight: 600, fontSize: 13 }}>
              <FormattedMessage id="TASK_ID" />:
            </span>
            <span
              style={{ color: primary, fontSize: 13, wordBreak: "break-all" }}
            >
              {taskId || "-"}
            </span>

            <button
              type="button"
              onClick={handleCopy}
              disabled={!taskId}
              title={copied ? "copy" : "copied"}
              style={{
                border: "none",
                background: "transparent",
                padding: 0,
                display: "inline-flex",
                alignItems: "center",
                cursor: taskId ? "pointer" : "not-allowed",
                color: primary,
              }}
              aria-label={copied ? "copied" : "copy"}
            >
              <span style={{ fontSize: 16, lineHeight: 1 }}>
                {copied ? (
                  <i className="bi bi-check"></i>
                ) : (
                  <i className="bi bi-copy"></i>
                )}
              </span>
            </button>
          </div>
        </div>

        <SectionTitle id="SCORES" />
        <div style={{ color: "#444" }}>
          <FormattedMessage id="SCORES_DESC" />
        </div>
        <BulletList items={["SCORES_IMAGE_SCORE", "SCORES_INDIVIDUAL_SCORE"]} />

        <img
          src={`${process.env.PUBLIC_URL}/images/MatchResultExample.png`}
          alt="Match Result Example"
          className="img-fluid w-100"
        />

        <SectionTitle id="PROJECT" />
        <div style={{ color: "#444" }}>
          <FormattedMessage id="PROJECT_DESC" />
        </div>

        <SectionTitle id="COMPARE_AND_SELECT_MATCHES" />
        <BulletList
          items={[
            "COMPARE_AND_SELECT_MATCHES_B1",
            "COMPARE_AND_SELECT_MATCHES_B2",
            "COMPARE_AND_SELECT_MATCHES_B3",
          ]}
        />

        <SectionTitle id="ASSIGN_ID" />
        <BulletList items={["ASSIGN_ID_B1", "ASSIGN_ID_B2", "ASSIGN_ID_B3"]} />

        <SectionTitle id="TOOLS" />
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <ZoomInIcon />
            <div>
              <FormattedMessage id="ZOOM_IN" />
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <ZoomOutIcon />
            <div>
              <FormattedMessage id="ZOOM_OUT" />
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <HatchMarkIcon />
            <div>
              <FormattedMessage id="INSPECT_TOOL_DESC" />
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <ToggoleAnnotationIcon />
            <div>
              <FormattedMessage id="ANNOTATION_TOOL_DESC" />
            </div>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <FullScreenIcon />
            <div>
              <FormattedMessage id="FULL_SCREEN_MODE_DESC" />
            </div>
          </div>
        </div>

        <div style={{ marginTop: 14, fontSize: 13 }}>
          <FormattedMessage id="FOR_FULL_INSTRUCTIONS_PREFIX" />{" "}
          <a
            href={
              "https://wildbook.docs.wildme.org/getting-started-with-wildbook.html"
            }
            target="_blank"
            rel="noopener noreferrer"
          >
            <FormattedMessage id="WILDBOOK_DOCUMENTATION" />
          </a>
        </div>
      </Modal.Body>
    </Modal>
  );
}
