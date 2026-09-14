import React from "react";
import { Modal } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import ToggleAnnotationIcon from "../icons/ToggleAnnotationIcon";
import HatchMarkIcon from "../icons/HatchMarkIcon";
import FullScreenIcon from "../icons/FullScreenIcon";

const SectionTitle = ({ id, testId }) => (
  <div
    style={{ fontWeight: 700, marginTop: 14, marginBottom: 6 }}
    data-testid={testId}
  >
    <FormattedMessage id={id} />
  </div>
);

const BulletList = ({ items, testIdPrefix }) => (
  <ul
    style={{ marginTop: 6, marginBottom: 6, paddingLeft: 20 }}
    data-testid={testIdPrefix ? `${testIdPrefix}-list` : undefined}
  >
    {items.map((id, idx) => (
      <li
        key={id}
        style={{ marginBottom: 4 }}
        data-testid={testIdPrefix ? `${testIdPrefix}-item-${idx}` : undefined}
      >
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
    <Modal
      show={show}
      size="xl"
      onHide={onHide}
      centered
      scrollable
      id="match-instructions-modal"
      data-testid="match-instructions-modal"
    >
      <Modal.Header
        closeButton
        id="match-instructions-modal-header"
        data-testid="match-instructions-modal-header"
      >
        <Modal.Title
          id="match-instructions-modal-title"
          data-testid="match-instructions-modal-title"
        >
          <FormattedMessage id="MATCHING_PAGE_INSTRUCTIONS" />
        </Modal.Title>
      </Modal.Header>

      <Modal.Body
        style={{ lineHeight: 1.35 }}
        id="match-instructions-modal-body"
        data-testid="match-instructions-modal-body"
      >
        <div
          style={{
            display: "flex",
            alignItems: "center",
            gap: 10,
            marginBottom: 10,
          }}
          id="match-instructions-taskid-row"
          data-testid="match-instructions-taskid-row"
        >
          <div
            style={{ display: "flex", alignItems: "center", gap: 6 }}
            id="match-instructions-taskid-wrap"
            data-testid="match-instructions-taskid-wrap"
          >
            <span
              style={{ color: "#333", fontWeight: 600, fontSize: 13 }}
              id="match-instructions-taskid-label"
              data-testid="match-instructions-taskid-label"
            >
              <FormattedMessage id="TASK_ID" />:
            </span>

            <span
              style={{ color: primary, fontSize: 13, wordBreak: "break-all" }}
              id="match-instructions-taskid-value"
              data-testid="match-instructions-taskid-value"
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
              id="match-instructions-copy-btn"
              data-testid="match-instructions-copy-btn"
            >
              <span
                style={{ fontSize: 16, lineHeight: 1 }}
                id="match-instructions-copy-icon"
                data-testid="match-instructions-copy-icon"
              >
                {copied ? (
                  <i className="bi bi-check"></i>
                ) : (
                  <i className="bi bi-copy"></i>
                )}
              </span>
            </button>
          </div>
        </div>

        <SectionTitle id="SCORES" testId="match-instructions-section-scores" />
        <div
          style={{ color: "#444" }}
          id="match-instructions-scores-desc"
          data-testid="match-instructions-scores-desc"
        >
          <FormattedMessage id="SCORES_DESC" />
        </div>
        <BulletList
          items={["SCORES_IMAGE_SCORE", "SCORES_INDIVIDUAL_SCORE"]}
          testIdPrefix="match-instructions-scores"
        />

        <img
          src={`${process.env.PUBLIC_URL}/images/MatchResultExample.png`}
          alt="Match Result Example"
          className="img-fluid w-100"
          id="match-instructions-example-image"
          data-testid="match-instructions-example-image"
        />

        <SectionTitle
          id="PROJECT"
          testId="match-instructions-section-project"
        />
        <div
          style={{ color: "#444" }}
          id="match-instructions-project-desc"
          data-testid="match-instructions-project-desc"
        >
          <FormattedMessage id="PROJECT_DESC" />
        </div>

        <SectionTitle
          id="COMPARE_AND_SELECT_MATCHES"
          testId="match-instructions-section-compare"
        />
        <BulletList
          items={[
            "COMPARE_AND_SELECT_MATCHES_B1",
            "COMPARE_AND_SELECT_MATCHES_B2",
            "COMPARE_AND_SELECT_MATCHES_B3",
          ]}
          testIdPrefix="match-instructions-compare"
        />

        <SectionTitle
          id="ASSIGN_ID"
          testId="match-instructions-section-assign"
        />
        <BulletList
          items={["ASSIGN_ID_B1", "ASSIGN_ID_B2", "ASSIGN_ID_B3"]}
          testIdPrefix="match-instructions-assign"
        />

        <SectionTitle id="TOOLS" testId="match-instructions-section-tools" />
        <div
          style={{ display: "flex", flexDirection: "column", gap: 10 }}
          id="match-instructions-tools"
          data-testid="match-instructions-tools"
        >
          <div
            style={{ display: "flex", alignItems: "center", gap: 10 }}
            id="match-instructions-tool-zoom-in"
            data-testid="match-instructions-tool-zoom-in"
          >
            <ZoomInIcon />
            <div>
              <FormattedMessage id="ZOOM_IN" />
            </div>
          </div>

          <div
            style={{ display: "flex", alignItems: "center", gap: 10 }}
            id="match-instructions-tool-zoom-out"
            data-testid="match-instructions-tool-zoom-out"
          >
            <ZoomOutIcon />
            <div>
              <FormattedMessage id="ZOOM_OUT" />
            </div>
          </div>

          <div
            style={{ display: "flex", alignItems: "center", gap: 10 }}
            id="match-instructions-tool-inspect"
            data-testid="match-instructions-tool-inspect"
          >
            <HatchMarkIcon />
            <div>
              <FormattedMessage id="INSPECT_TOOL_DESC" />
            </div>
          </div>

          <div
            style={{ display: "flex", alignItems: "center", gap: 10 }}
            id="match-instructions-tool-annotations"
            data-testid="match-instructions-tool-annotations"
          >
            <ToggleAnnotationIcon />
            <div>
              <FormattedMessage id="ANNOTATION_TOOL_DESC" />
            </div>
          </div>

          <div
            style={{ display: "flex", alignItems: "center", gap: 10 }}
            id="match-instructions-tool-fullscreen"
            data-testid="match-instructions-tool-fullscreen"
          >
            <FullScreenIcon />
            <div>
              <FormattedMessage id="FULL_SCREEN_MODE_DESC" />
            </div>
          </div>
        </div>

        <div
          style={{ marginTop: 14, fontSize: 13 }}
          id="match-instructions-footer"
          data-testid="match-instructions-footer"
        >
          <FormattedMessage id="FOR_FULL_INSTRUCTIONS_PREFIX" />{" "}
          <a
            href="https://wildbook.docs.wildme.org/getting-started-with-wildbook.html"
            target="_blank"
            rel="noopener noreferrer"
            id="match-instructions-doc-link"
            data-testid="match-instructions-doc-link"
          >
            <FormattedMessage id="WILDBOOK_DOCUMENTATION" />
          </a>
        </div>
      </Modal.Body>
    </Modal>
  );
}
