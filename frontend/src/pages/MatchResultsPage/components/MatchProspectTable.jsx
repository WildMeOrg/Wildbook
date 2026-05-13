import React, { useRef, useState } from "react";
import { Row, Col, Form, Modal, Spinner } from "react-bootstrap";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import HatchMarkIcon from "../icons/HatchMarkIcon";
import ToggleAnnotationIcon from "../icons/ToggleAnnotationIcon";
import FullScreenIcon from "../icons/FullScreenIcon";
import InteractiveAnnotationOverlay from "../../../components/AnnotationOverlay";
import { FormattedMessage, useIntl } from "react-intl";
import InspectorModal from "./InspectorModal";
import ExitFullScreenIcon from "../icons/ExitFullScreenIcon";
import EncounterIcon from "../../../components/icons/EncounterIcon";
import EmptyMatchPlaceholder from "./EmptyMatchPlaceholder";

const styles = {
  matchRow: (selected, themeColor) => ({
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "6px 10px",
    fontSize: "1rem",
    marginTop: "4px",
    borderRadius: "5px",
    backgroundColor: selected
      ? themeColor.primaryColors.primary50
      : "transparent",
  }),
  matchRank: {
    width: "24px",
    textAlign: "right",
    marginRight: "8px",
  },
  idPill: (themeColor) => ({
    borderRadius: "5px",
    border: "none",
    padding: "2px 10px",
    fontSize: "1rem",
    background: themeColor.wildMeColors.teal100,
    color: themeColor.wildMeColors.teal800,
    maxWidth: "200px",
    overflow: "hidden",
  }),
  encounterButton: () => ({
    borderRadius: "50%",
    border: "none",
    fontSize: "1rem",
    display: "flex",
    alignItems: "center",
    gap: "4px",
    width: "20px",
    height: "20px",
    padding: 0,
    lineHeight: 0,
  }),
  matchImageCard: {
    position: "relative",
    borderRadius: "8px",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
    overflow: "hidden",
  },
  imageContainer: {
    width: "100%",
    height: "100%",
    overflow: "hidden",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#f8f9fa",
  },
  cornerLabel: (themeColor) => ({
    position: "absolute",
    top: "8px",
    left: "-8px",
    background: themeColor.wildMeColors.teal100,
    color: themeColor.wildMeColors.teal800,
    padding: "2px 8px",
    borderRadius: "2px",
    fontSize: "1rem",
    zIndex: 10,
  }),
  toolsBarLeft: {
    position: "absolute",
    top: "0",
    left: "-40px",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  toolsBarRight: {
    position: "absolute",
    top: "0",
    right: "-40px",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  iconButton: {
    width: "32px",
    height: "32px",
    borderRadius: "8px",
    cursor: "pointer",
  },
  iconButtonDisabled: {
    width: "32px",
    height: "32px",
    borderRadius: "8px",
    cursor: "not-allowed",
    opacity: 0.4,
  },
  matchListScrollContainer: {
    overflowX: "auto",
    overflowY: "hidden",
    marginBottom: "1rem",
  },
  matchListGrid: {
    display: "flex",
    gap: "12px",
    width: "100%",
  },
  matchColumn: {
    flex: 1,
    minWidth: "30%",
    display: "flex",
    flexDirection: "column",
  },
  fullscreenBody: {
    padding: 12,
    background: "#111",
    height: "100vh",
  },
  fullscreenGrid: {
    height: "calc(100vh - 24px)",
    display: "flex",
    gap: 12,
  },
  fullscreenPanel: {
    flex: 1,
    minWidth: 0,
    borderRadius: 10,
    overflow: "hidden",
    background: "#1a1a1a",
    position: "relative",
    boxShadow: "0 2px 14px rgba(0,0,0,0.35)",
  },
  fullscreenLabel: {
    position: "absolute",
    top: 10,
    left: 10,
    zIndex: 5,
    background: "rgba(255,255,255,0.92)",
    padding: "3px 10px",
    borderRadius: 6,
    fontSize: 12,
  },
  fullscreenImageWrap: {
    position: "relative",
    width: "100%",
    height: "100%",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    background: "#111",
  },
  fullscreenTopRight: {
    position: "absolute",
    top: 10,
    right: 10,
    zIndex: 80,
    display: "flex",
    gap: 8,
  },
};

const MatchProspectTable = ({
  sectionId,
  numCandidates,
  date,
  selectedMatch,
  onToggleSelected,
  thisEncounterImageUrl,
  thisEncounterAnnotations,
  thisEncounterImageAsset,
  themeColor,
  columns = [],
  algorithm,
  methodName,
  methodDescription,
  taskStatusOverall,
  emptyStateType,
  errors,
}) => {
  const intl = useIntl();
  const matchesBasedOnText = intl.formatMessage({ id: "MATCHED_BASED_ON" });
  const leftOverlayRef = useRef(null);
  const rightOverlayRef = useRef(null);

  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const fsLeftRef = useRef(null);
  const fsRightRef = useRef(null);

  const hasProspects = columns.some((columnData) =>
    columnData.some((candidate) => candidate?.annotation),
  );

  const [previewedRow, setPreviewedRow] = useState(() => {
    const first =
      columns
        .flatMap((columnData) => columnData)
        .find((candidate) => candidate?.annotation) ?? null;
    if (!first) return null;
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    return { ...first, _rowKey: firstKey };
  });

  const [inspectorOpen, setInspectorOpen] = useState(false);
  const inspectorUrl = previewedRow?.asset?.url;
  const inspectorOrigW = previewedRow?.asset?.width;
  const inspectorOrigH = previewedRow?.asset?.height;

  React.useEffect(() => {
    const flat = columns.flatMap((columnData) => columnData);
    const candidates = flat.filter((candidate) => candidate?.annotation);

    if (candidates.length === 0) {
      setPreviewedRow(null);
      return;
    }

    setPreviewedRow((prev) => {
      if (prev?.annotation?.id) {
        const matched = candidates.find(
          (candidate) => candidate?.annotation?.id === prev.annotation.id,
        );

        if (matched) {
          const matchedKey = `${matched.annotation?.id}-${matched.displayIndex}`;
          return { ...matched, _rowKey: matchedKey };
        }
      }

      const first = candidates[0];
      const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
      return { ...first, _rowKey: firstKey };
    });
  }, [columns]);

  const [hoveredRow, setHoveredRow] = React.useState(null);

  const handleRowClick = (rowData, rowKey) => {
    setPreviewedRow({ ...rowData, _rowKey: rowKey });
    rightOverlayRef.current?.reset?.();
  };

  const isSelected = (rowKey) => selectedMatch?.some((d) => d.key === rowKey);

  const rightAnnotations = React.useMemo(() => {
    const ann = previewedRow?.annotation;
    if (!ann) return [];
    return [
      {
        id: ann.id,
        boundingBox: ann.boundingBox,
        x: ann.x,
        y: ann.y,
        width: ann.width,
        height: ann.height,
        theta: ann.theta,
        trivial: ann.isTrivial || ann.trivial,
      },
    ];
  }, [previewedRow]);

  const rightImageUrl = previewedRow?.annotation?.asset?.url;

  const leftOrigW =
    thisEncounterImageAsset?.attributes?.width ??
    thisEncounterImageAsset?.width;
  const leftOrigH =
    thisEncounterImageAsset?.attributes?.height ??
    thisEncounterImageAsset?.height;

  const leftAnnotations = thisEncounterAnnotations;
  const leftRotationInfo = thisEncounterImageAsset?.rotationInfo;

  const rightOrigW =
    previewedRow?.annotation?.asset?.width ??
    previewedRow?.annotation?.asset?.attributes?.width;
  const rightOrigH =
    previewedRow?.annotation?.asset?.height ??
    previewedRow?.annotation?.asset?.attributes?.height;

  const leftImageUrl = thisEncounterImageUrl;
  const hasLeftImage = Boolean(leftImageUrl);
  const hasRightImage = Boolean(rightImageUrl);

  const openFullscreen = () => {
    if (!hasRightImage) return;
    setFullscreenOpen(true);
  };

  React.useEffect(() => {
    if (!fullscreenOpen) return;
    fsLeftRef.current?.reset?.();
    fsRightRef.current?.reset?.();
  }, [fullscreenOpen]);

  const isStillRunning =
    !!taskStatusOverall &&
    taskStatusOverall !== "completed" &&
    taskStatusOverall !== "error";

  const isError = taskStatusOverall === "error";

  return (
    <div
      className="mb-4"
      id={sectionId}
      data-testid={`match-prospect-table-${sectionId}`}
    >
      <div
        className="d-flex justify-content-between align-items-center mb-2"
        data-testid={`match-prospect-header-${sectionId}`}
      >
        <div className="d-flex w-100">
          <div
            style={{ fontWeight: "500" }}
            data-testid={`match-prospect-method-${sectionId}`}
          >
            {methodDescription
              ? `${matchesBasedOnText} ${methodDescription}`
              : methodName
                ? `${matchesBasedOnText} ${methodName}`
                : algorithm}
          </div>

          <div
            className="d-flex flex-row gap-3"
            style={{ marginLeft: "auto" }}
            data-testid={`match-prospect-meta-${sectionId}`}
          >
            <div
              style={{
                backgroundColor: themeColor.primaryColors.primary50,
                borderRadius: "4px",
                padding: "4px",
              }}
              data-testid={`match-prospect-candidates-${sectionId}`}
            >
              <FormattedMessage id="AGAINST" /> {numCandidates}{" "}
              <FormattedMessage id="CANDIDATES" />{" "}
            </div>

            <div
              style={{
                backgroundColor: themeColor.primaryColors.primary50,
                borderRadius: "4px",
                padding: "4px",
              }}
              data-testid={`match-prospect-date-${sectionId}`}
            >
              <span>{date?.slice(0, 16)?.replace("T", " ")}</span>
            </div>
          </div>
        </div>
      </div>

      <div
        style={styles.matchListScrollContainer}
        data-testid={`match-prospect-list-scroll-${sectionId}`}
      >
        {hasProspects ? (
          <div
            style={styles.matchListGrid}
            data-testid={`match-prospect-list-${sectionId}`}
          >
            {columns.map((columnData, columnIndex) => (
              <div
                key={columnIndex}
                style={styles.matchColumn}
                data-testid={`match-prospect-column-${sectionId}-${columnIndex}`}
              >
                {columnData
                  .filter((candidate) => candidate?.annotation)
                  .map((candidate) => {
                    const candidateEncounterId =
                      candidate.annotation?.encounter?.id;
                    const candidateIndividualId =
                      candidate.annotation?.individual?.id;
                    const candidateIndividualDisplayName =
                      candidate.annotation?.individual?.displayName;

                    const canOpenEncounter = Boolean(candidateEncounterId);
                    const canOpenIndividual = Boolean(candidateIndividualId);

                    const rowKey = `${candidate.annotation?.id ?? candidate.annotation?.encounter?.id ?? "no-annot"}-${candidate.displayIndex ?? "no-idx"}`;
                    const isRowSelected = isSelected(rowKey);
                    const isRowPreviewed = rowKey === previewedRow?._rowKey;
                    const isRowHovered = rowKey === hoveredRow;

                    return (
                      <div
                        key={rowKey}
                        id={`match-prospect-row-${sectionId}-${rowKey}`}
                        data-testid={`match-prospect-row-${sectionId}-${rowKey}`}
                        onClick={() => handleRowClick(candidate, rowKey)}
                        style={{
                          ...styles.matchRow(isRowSelected, themeColor),
                          cursor: "pointer",
                          backgroundColor:
                            isRowPreviewed || isRowHovered
                              ? themeColor.primaryColors.primary50
                              : "transparent",
                        }}
                        onMouseEnter={() => setHoveredRow(rowKey)}
                        onMouseLeave={() => setHoveredRow(null)}
                      >
                        <span
                          style={styles.matchRank}
                          data-testid={`match-prospect-rank-${sectionId}-${rowKey}`}
                        >
                          {candidate.displayIndex}.
                        </span>

                        <span
                          id={`match-prospect-score-${sectionId}-${rowKey}`}
                          data-testid={`match-prospect-score-${sectionId}-${rowKey}`}
                          style={{
                            maxWidth: "150px",
                            overflow: "hidden",
                          }}
                        >
                          {Number.isFinite(candidate?.score)
                            ? Math.max(candidate.score, 0).toLocaleString(
                                undefined,
                                { maximumFractionDigits: 4 },
                              )
                            : "—"}
                        </span>

                        <button
                          type="button"
                          id={`match-prospect-individual-${sectionId}-${rowKey}`}
                          data-testid={`match-prospect-individual-${sectionId}-${rowKey}`}
                          style={styles.idPill(themeColor)}
                          className="btn btn-sm p-0 px-2"
                          onClick={(e) => {
                            e.stopPropagation();
                            if (!canOpenIndividual) return;
                            const url = `/individuals.jsp?id=${encodeURIComponent(candidateIndividualId)}`;
                            window.open(url, "_blank");
                          }}
                        >
                          {candidateIndividualDisplayName ||
                            candidateIndividualId}
                        </button>

                        {(isRowHovered || isRowSelected) && (
                          <button
                            type="button"
                            title={
                              canOpenEncounter
                                ? `Open Encounter Page (${candidateEncounterId})`
                                : "Open Encounter Page"
                            }
                            id={`match-prospect-encounter-btn-${sectionId}-${rowKey}`}
                            data-testid={`match-prospect-encounter-btn-${sectionId}-${rowKey}`}
                            style={styles.encounterButton(themeColor)}
                            className="btn btn-sm p-0 px-2"
                            onClick={(e) => {
                              e.stopPropagation();
                              if (!canOpenEncounter) return;
                              const url = `/react/encounter?number=${encodeURIComponent(candidateEncounterId)}`;
                              window.open(url, "_blank");
                            }}
                          >
                            <EncounterIcon />
                          </button>
                        )}

                        <div style={{ flexGrow: 1 }} />

                        <div
                          style={{
                            display: "flex",
                            alignItems: "center",
                            gap: "20px",
                          }}
                          onClick={(e) => e.stopPropagation()}
                          data-testid={`match-prospect-actions-${sectionId}-${rowKey}`}
                        >
                          <Form.Check
                            type="checkbox"
                            id={`match-prospect-select-${sectionId}-${rowKey}`}
                            data-testid={`match-prospect-select-${sectionId}-${rowKey}`}
                            checked={isRowSelected}
                            onChange={(e) =>
                              onToggleSelected(
                                e.target.checked,
                                rowKey,
                                candidateEncounterId,
                                candidateIndividualId,
                                candidateIndividualDisplayName,
                              )
                            }
                          />
                        </div>
                      </div>
                    );
                  })}
              </div>
            ))}
          </div>
        ) : (
          <div className="mt-3">
            {isStillRunning ? (
              <div
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "8px",
                }}
                data-testid={`match-prospect-running-${sectionId}`}
              >
                <FormattedMessage
                  id="MATCH_RESULTS_STILL_PROCESSING"
                  defaultMessage="Match results are still being processed."
                />
                <Spinner
                  animation="border"
                  size="sm"
                  role="status"
                  aria-label="Loading match results"
                />
              </div>
            ) : isError ? (
              <div
                className="alert alert-danger mt-3 mb-0"
                role="alert"
                data-testid={`match-prospect-error-${sectionId}`}
              >
                <div className="fw-semibold mb-1">
                  <FormattedMessage
                    id="MATCH_RESULTS_PROCESSING_FAILED"
                    defaultMessage="Match results processing failed."
                  />
                </div>

                {Array.isArray(errors) && errors.length > 0 && (
                  <>
                    <div className="small mb-1">
                      <FormattedMessage
                        id="AN_ERROR_OCCURRED"
                        defaultMessage="An error occurred"
                      />
                    </div>

                    <ul className="mb-0 ps-3">
                      {errors.map((err, index) => (
                        <li key={`${err?.code || "unknown"}-${index}`}>
                          <FormattedMessage
                            id={`MATCH_RESULTS_ERROR_${err?.code}`}
                            defaultMessage="Unknown error"
                          />
                        </li>
                      ))}
                    </ul>
                  </>
                )}
              </div>
            ) : (
              <div
                className="alert alert-danger mt-3 mb-0"
                role="alert"
                data-testid={`match-prospect-empty-${sectionId}`}
              >
                {emptyStateType === "no_candidates" ||
                emptyStateType === "no_prospects" ? (
                  <FormattedMessage id="NO_MATCH_PROSPECTS" />
                ) : (
                  <FormattedMessage
                    id="NO_MATCH_RESULT"
                    defaultMessage="No match results available."
                  />
                )}
              </div>
            )}
          </div>
        )}
      </div>

      <Row data-testid={`match-prospect-images-${sectionId}`}>
        <Col
          md={6}
          className="mb-3 mb-md-0"
          style={{ position: "relative" }}
          data-testid={`match-prospect-left-col-${sectionId}`}
        >
          <div
            style={styles.matchImageCard}
            data-testid={`match-prospect-left-card-${sectionId}`}
          >
            <div
              style={styles.cornerLabel(themeColor)}
              data-testid={`match-prospect-left-label-${sectionId}`}
            >
              <FormattedMessage id="THIS_ENCOUNTER" />
            </div>
            <div
              style={styles.imageContainer}
              data-testid={`match-prospect-left-overlay-wrap-${sectionId}`}
            >
              {hasLeftImage ? (
                <InteractiveAnnotationOverlay
                  ref={leftOverlayRef}
                  imageUrl={leftImageUrl}
                  originalWidth={leftOrigW}
                  originalHeight={leftOrigH}
                  annotations={leftAnnotations}
                  rotationInfo={leftRotationInfo}
                />
              ) : (
                <EmptyMatchPlaceholder sectionId={`${sectionId}-left`} />
              )}
            </div>
          </div>

          <div
            style={styles.toolsBarLeft}
            data-testid={`match-prospect-left-toolbar-${sectionId}`}
          >
            <div
              onClick={() => leftOverlayRef.current?.zoomIn?.()}
              style={
                hasLeftImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom In"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  leftOverlayRef.current?.zoomIn?.();
                }
              }}
              id={`match-prospect-left-zoom-in-${sectionId}`}
              data-testid={`match-prospect-left-zoom-in-${sectionId}`}
              aria-disabled={!hasLeftImage}
            >
              <ZoomInIcon />
            </div>

            <div
              onClick={() => leftOverlayRef.current?.zoomOut?.()}
              style={
                hasLeftImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom Out"
              role="button"
              tabIndex={0}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  leftOverlayRef.current?.zoomOut?.();
                }
              }}
              id={`match-prospect-left-zoom-out-${sectionId}`}
              data-testid={`match-prospect-left-zoom-out-${sectionId}`}
              aria-disabled={!hasLeftImage}
            >
              <ZoomOutIcon />
            </div>
          </div>
        </Col>

        <Col
          md={6}
          style={{ position: "relative" }}
          data-testid={`match-prospect-right-col-${sectionId}`}
        >
          <div
            style={styles.matchImageCard}
            data-testid={`match-prospect-right-card-${sectionId}`}
          >
            <div
              style={{ ...styles.cornerLabel(themeColor) }}
              data-testid={`match-prospect-right-label-${sectionId}`}
            >
              <FormattedMessage id="POSSIBLE_MATCH" />
            </div>
            <div
              style={styles.imageContainer}
              data-testid={`match-prospect-right-overlay-wrap-${sectionId}`}
            >
              {hasRightImage ? (
                <InteractiveAnnotationOverlay
                  ref={rightOverlayRef}
                  imageUrl={rightImageUrl}
                  originalWidth={rightOrigW}
                  originalHeight={rightOrigH}
                  annotations={rightAnnotations}
                  rotationInfo={
                    previewedRow?.annotation?.asset?.rotationInfo ?? null
                  }
                />
              ) : (
                <EmptyMatchPlaceholder sectionId={sectionId} />
              )}
            </div>
          </div>

          <div
            style={styles.toolsBarRight}
            data-testid={`match-prospect-right-toolbar-${sectionId}`}
          >
            <div
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.zoomIn?.();
              }}
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom In"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.zoomIn?.();
                }
              }}
              id={`match-prospect-right-zoom-in-${sectionId}`}
              data-testid={`match-prospect-right-zoom-in-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ZoomInIcon />
            </div>

            <div
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.zoomOut?.();
              }}
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Zoom Out"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.zoomOut?.();
                }
              }}
              id={`match-prospect-right-zoom-out-${sectionId}`}
              data-testid={`match-prospect-right-zoom-out-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ZoomOutIcon />
            </div>

            <div
              style={
                inspectorUrl && hasRightImage
                  ? styles.iconButton
                  : styles.iconButtonDisabled
              }
              title={
                inspectorUrl
                  ? "View inspection visualization"
                  : "No visualization available"
              }
              role="button"
              tabIndex={inspectorUrl && hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!inspectorUrl || !hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  setInspectorOpen(true);
                }
              }}
              onClick={() => {
                if (inspectorUrl && hasRightImage) setInspectorOpen(true);
              }}
              id={`match-prospect-inspector-open-${sectionId}`}
              data-testid={`match-prospect-inspector-open-${sectionId}`}
              aria-disabled={!inspectorUrl || !hasRightImage}
            >
              <HatchMarkIcon />
            </div>

            <div
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="View Annotations"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (!hasRightImage) return;
                if (e.key === "Enter" || e.key === " ") {
                  rightOverlayRef.current?.toggleAnnotations?.();
                  leftOverlayRef.current?.toggleAnnotations?.();
                }
              }}
              onClick={() => {
                if (!hasRightImage) return;
                rightOverlayRef.current?.toggleAnnotations?.();
                leftOverlayRef.current?.toggleAnnotations?.();
              }}
              id={`match-prospect-toggle-annotations-${sectionId}`}
              data-testid={`match-prospect-toggle-annotations-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <ToggleAnnotationIcon />
            </div>

            <div
              style={
                hasRightImage ? styles.iconButton : styles.iconButtonDisabled
              }
              title="Fullscreen"
              role="button"
              tabIndex={hasRightImage ? 0 : -1}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  if (!hasRightImage) return;
                  openFullscreen();
                }
              }}
              onClick={(e) => {
                e.stopPropagation();
                if (!hasRightImage) return;
                openFullscreen();
              }}
              id={`match-prospect-fullscreen-open-${sectionId}`}
              data-testid={`match-prospect-fullscreen-open-${sectionId}`}
              aria-disabled={!hasRightImage}
            >
              <FullScreenIcon />
            </div>
          </div>
        </Col>
      </Row>

      {hasProspects && hasRightImage && (
        <Modal
          show={fullscreenOpen}
          onHide={() => setFullscreenOpen(false)}
          fullscreen
          centered={false}
          keyboard
          contentClassName="border-0 rounded-0"
          data-testid={`match-prospect-fullscreen-modal-${sectionId}`}
        >
          <div
            style={styles.fullscreenBody}
            data-testid={`match-prospect-fullscreen-body-${sectionId}`}
          >
            <div
              style={styles.fullscreenGrid}
              data-testid={`match-prospect-fullscreen-grid-${sectionId}`}
            >
              <div
                style={styles.fullscreenPanel}
                data-testid={`match-prospect-fullscreen-left-panel-${sectionId}`}
              >
                <div
                  style={styles.fullscreenImageWrap}
                  data-testid={`match-prospect-fullscreen-left-wrap-${sectionId}`}
                >
                  <div
                    style={styles.fullscreenLabel}
                    data-testid={`match-prospect-fullscreen-left-label-${sectionId}`}
                  >
                    <FormattedMessage id="THIS_ENCOUNTER" />
                  </div>

                  <div
                    style={styles.fullscreenTopRight}
                    data-testid={`match-prospect-fullscreen-left-toolbar-${sectionId}`}
                  >
                    <div
                      style={styles.iconButton}
                      title="Zoom In"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsLeftRef.current?.zoomIn?.();
                        }
                      }}
                      onClick={() => fsLeftRef.current?.zoomIn?.()}
                      id={`match-prospect-fullscreen-left-zoom-in-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-left-zoom-in-${sectionId}`}
                    >
                      <ZoomInIcon />
                    </div>
                    <div
                      style={styles.iconButton}
                      title="Zoom Out"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsLeftRef.current?.zoomOut?.();
                        }
                      }}
                      onClick={() => fsLeftRef.current?.zoomOut?.()}
                      id={`match-prospect-fullscreen-left-zoom-out-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-left-zoom-out-${sectionId}`}
                    >
                      <ZoomOutIcon />
                    </div>
                  </div>

                  <InteractiveAnnotationOverlay
                    ref={fsLeftRef}
                    imageUrl={leftImageUrl}
                    originalWidth={leftOrigW}
                    originalHeight={leftOrigH}
                    annotations={leftAnnotations}
                    rotationInfo={leftRotationInfo}
                  />
                </div>
              </div>

              <div
                style={styles.fullscreenPanel}
                data-testid={`match-prospect-fullscreen-right-panel-${sectionId}`}
              >
                <div
                  style={styles.fullscreenImageWrap}
                  data-testid={`match-prospect-fullscreen-right-wrap-${sectionId}`}
                >
                  <div
                    style={styles.fullscreenLabel}
                    data-testid={`match-prospect-fullscreen-right-label-${sectionId}`}
                  >
                    <FormattedMessage id="POSSIBLE_MATCH" />
                  </div>

                  <div
                    style={styles.fullscreenTopRight}
                    data-testid={`match-prospect-fullscreen-right-toolbar-${sectionId}`}
                  >
                    <div
                      style={styles.iconButton}
                      title="Zoom In"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsRightRef.current?.zoomIn?.();
                        }
                      }}
                      onClick={() => fsRightRef.current?.zoomIn?.()}
                      id={`match-prospect-fullscreen-right-zoom-in-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-right-zoom-in-${sectionId}`}
                    >
                      <ZoomInIcon />
                    </div>
                    <div
                      style={styles.iconButton}
                      title="Zoom Out"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsRightRef.current?.zoomOut?.();
                        }
                      }}
                      onClick={() => fsRightRef.current?.zoomOut?.()}
                      id={`match-prospect-fullscreen-right-zoom-out-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-right-zoom-out-${sectionId}`}
                    >
                      <ZoomOutIcon />
                    </div>

                    <div
                      style={
                        inspectorUrl
                          ? styles.iconButton
                          : styles.iconButtonDisabled
                      }
                      title={
                        inspectorUrl
                          ? "View inspection visualization"
                          : "No visualization available"
                      }
                      role="button"
                      tabIndex={inspectorUrl ? 0 : -1}
                      onKeyDown={(e) => {
                        if (!inspectorUrl) return;
                        if (e.key === "Enter" || e.key === " ")
                          setInspectorOpen(true);
                      }}
                      onClick={() => {
                        if (inspectorUrl) setInspectorOpen(true);
                      }}
                      id={`match-prospect-fullscreen-inspector-open-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-inspector-open-${sectionId}`}
                      aria-disabled={!inspectorUrl}
                    >
                      <HatchMarkIcon />
                    </div>

                    <div
                      style={styles.iconButton}
                      title="View Annotations"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          fsRightRef.current?.toggleAnnotations?.();
                          fsLeftRef.current?.toggleAnnotations?.();
                        }
                      }}
                      onClick={() => {
                        fsRightRef.current?.toggleAnnotations?.();
                        fsLeftRef.current?.toggleAnnotations?.();
                      }}
                      id={`match-prospect-fullscreen-toggle-annotations-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-toggle-annotations-${sectionId}`}
                    >
                      <ToggleAnnotationIcon />
                    </div>

                    <div
                      style={styles.iconButton}
                      title="Exit fullscreen"
                      role="button"
                      tabIndex={0}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          setFullscreenOpen(false);
                        }
                      }}
                      onClick={() => setFullscreenOpen(false)}
                      id={`match-prospect-fullscreen-exit-${sectionId}`}
                      data-testid={`match-prospect-fullscreen-exit-${sectionId}`}
                    >
                      <ExitFullScreenIcon />
                    </div>
                  </div>

                  <InteractiveAnnotationOverlay
                    ref={fsRightRef}
                    imageUrl={rightImageUrl}
                    originalWidth={rightOrigW}
                    originalHeight={rightOrigH}
                    annotations={rightAnnotations}
                    rotationInfo={
                      previewedRow?.annotation?.asset?.rotationInfo ?? null
                    }
                  />
                </div>
              </div>
            </div>
          </div>
        </Modal>
      )}

      <InspectorModal
        show={inspectorOpen}
        onHide={() => setInspectorOpen(false)}
        imageUrl={inspectorUrl}
        originalWidth={inspectorOrigW}
        originalHeight={inspectorOrigH}
      />
    </div>
  );
};

export default MatchProspectTable;
