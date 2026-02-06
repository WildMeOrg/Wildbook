import React, { useRef, useState } from "react";
import { Row, Col, Form, Modal } from "react-bootstrap";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import HatchMarkIcon from "../icons/HatchMarkIcon";
import ToggoleAnnotationIcon from "../icons/ToggoleAnnotationIcon";
import FullScreenIcon from "../icons/FullScreenIcon";
import InteractiveAnnotationOverlay from "../../../components/AnnotationOverlay";
import { FormattedMessage, useIntl } from "react-intl";
import InspectorModal from "./InspectorModal";
import ExitFullScreenIcon from "../icons/ExitFullScreenIcon";
import EncounterIcon from "../../../components/icons/EncounterIcon";

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
}) => {
  const intl = useIntl();
  const matchesBasedOnText = intl.formatMessage({ id: "MATCHED_BASED_ON" });
  const leftOverlayRef = useRef(null);
  const rightOverlayRef = useRef(null);

  const [fullscreenOpen, setFullscreenOpen] = useState(false);
  const fsLeftRef = useRef(null);
  const fsRightRef = useRef(null);

  const [previewedRow, setPreviewedRow] = useState(() => {
    const first = columns?.[0]?.[0] ?? null;
    if (!first) return null;
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    return { ...first, _rowKey: firstKey };
  });

  const [inspectorOpen, setInspectorOpen] = useState(false);
  const inspectorUrl = previewedRow?.asset?.url;
  const inspectorOrigW = previewedRow?.asset?.width;
  const inspectorOrigH = previewedRow?.asset?.height;

  React.useEffect(() => {
    const first = columns?.[0]?.[0] ?? null;
    if (!first) {
      setPreviewedRow(null);
      return;
    }
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    setPreviewedRow({ ...first, _rowKey: firstKey });
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

  const openFullscreen = () => {
    setFullscreenOpen(true);
    setTimeout(() => {
      fsLeftRef.current?.reset?.();
      fsRightRef.current?.reset?.();
    }, 0);
  };

  if (columns.length === 0) {
    return <FormattedMessage id="NO_MATCH_RESULT" />;
  }

  return (
    <div className="mb-4" id={sectionId}>
      <div className="d-flex justify-content-between align-items-center mb-2">
        <div className="d-flex w-100">
          <div style={{ fontWeight: "500" }}>
            {methodName
              ? `${matchesBasedOnText}${" "} ${methodName}`
              : `${matchesBasedOnText}${" "} ${algorithm}`}
          </div>
          <div className="d-flex flex-row gap-3" style={{ marginLeft: "auto" }}>
            <div
              style={{
                backgroundColor: themeColor.primaryColors.primary50,
                borderRadius: "4px",
                padding: "4px",
              }}
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
            >
              <span>{date?.slice(0, 16).replace("T", " ")}</span>
            </div>
          </div>
        </div>
      </div>

      <div style={styles.matchListScrollContainer}>
        <div style={styles.matchListGrid}>
          {columns.map((columnData, columnIndex) => (
            <div key={columnIndex} style={styles.matchColumn}>
              {columnData.map((candidate) => {
                const candidateEncounterId =
                  candidate.annotation?.encounter?.id;
                const candidateIndividualId =
                  candidate.annotation?.individual?.id;
                const candidateIndividualDisplayName =
                  candidate.annotation?.individual?.displayName;

                const rowKey = `${candidate.annotation?.id ?? candidate.annotation?.encounter?.id ?? "no-annot"}-${candidate.displayIndex ?? "no-idx"}`;
                const isRowSelected = isSelected(rowKey);
                const isRowPreviewed = rowKey === previewedRow?._rowKey;
                const isRowHovered = rowKey === hoveredRow;

                return (
                  <div
                    key={rowKey}
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
                    <span style={styles.matchRank}>
                      {candidate.displayIndex}.
                    </span>

                    <a
                      href={`/react/encounter?number=${candidateEncounterId}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      style={{
                        textDecoration: "none",
                        color: themeColor.primaryColors.primary500,
                      }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      {Number.isFinite(candidate?.score)
                        ? candidate.score.toLocaleString(undefined, {
                            maximumFractionDigits: 4,
                          })
                        : "—"}
                    </a>

                    <button
                      type="button"
                      style={styles.idPill(themeColor)}
                      className="btn btn-sm p-0 px-2"
                      onClick={(e) => {
                        e.stopPropagation();
                        const url = `/individuals.jsp?id=${candidateIndividualId}`;
                        window.open(url, "_blank");
                      }}
                    >
                      {candidateIndividualDisplayName || candidateIndividualId}
                    </button>

                    {(isRowHovered || isRowSelected) && (
                      <button
                        type="button"
                        style={styles.encounterButton(themeColor)}
                        className="btn btn-sm p-0 px-2"
                        onClick={(e) => {
                          e.stopPropagation();
                          const url = `/react/encounter?number=${candidateEncounterId}`;
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
                    >
                      <Form.Check
                        type="checkbox"
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
      </div>

      <Row>
        <Col md={6} className="mb-3 mb-md-0" style={{ position: "relative" }}>
          <div style={styles.matchImageCard}>
            <div style={styles.cornerLabel(themeColor)}>
              <FormattedMessage id="THIS_ENCOUNTER" />
            </div>
            <div style={styles.imageContainer}>
              <InteractiveAnnotationOverlay
                ref={leftOverlayRef}
                imageUrl={leftImageUrl}
                originalWidth={leftOrigW}
                originalHeight={leftOrigH}
                annotations={leftAnnotations}
                rotationInfo={leftRotationInfo}
              />
            </div>
          </div>

          <div style={styles.toolsBarLeft}>
            <div
              onClick={() => leftOverlayRef.current?.zoomIn?.()}
              style={styles.iconButton}
              title="Zoom In"
            >
              <ZoomInIcon />
            </div>
            <div
              onClick={() => leftOverlayRef.current?.zoomOut?.()}
              style={styles.iconButton}
              title="Zoom Out"
            >
              <ZoomOutIcon />
            </div>
          </div>
        </Col>

        <Col md={6} style={{ position: "relative" }}>
          <div style={styles.matchImageCard}>
            <div style={{ ...styles.cornerLabel(themeColor) }}>
              <FormattedMessage id="POSSIBLE_MATCH" />
            </div>
            <div style={styles.imageContainer}>
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
            </div>
          </div>

          <div style={styles.toolsBarRight}>
            <div
              onClick={() => rightOverlayRef.current?.zoomIn?.()}
              style={styles.iconButton}
              title="Zoom In"
            >
              <ZoomInIcon />
            </div>
            <div
              onClick={() => rightOverlayRef.current?.zoomOut?.()}
              style={styles.iconButton}
              title="Zoom Out"
            >
              <ZoomOutIcon />
            </div>
            <div
              style={
                inspectorUrl ? styles.iconButton : styles.iconButtonDisabled
              }
              title={
                inspectorUrl
                  ? "View Hotspotter Visualization"
                  : "No visualization available"
              }
              onClick={() => {
                if (inspectorUrl) {
                  setInspectorOpen(true);
                }
              }}
            >
              <HatchMarkIcon />
            </div>

            <div
              style={styles.iconButton}
              title="View Annotations"
              onClick={() => {
                rightOverlayRef.current?.toggleAnnotations?.();
                leftOverlayRef.current?.toggleAnnotations?.();
              }}
            >
              <ToggoleAnnotationIcon />
            </div>

            <div
              style={styles.iconButton}
              title="Fullscreen"
              onClick={(e) => {
                e.stopPropagation();
                if (!previewedRow) return;
                openFullscreen();
              }}
            >
              <FullScreenIcon />
            </div>
          </div>
        </Col>
      </Row>
      <Modal
        show={fullscreenOpen}
        onHide={() => setFullscreenOpen(false)}
        fullscreen
        centered={false}
        keyboard
        contentClassName="border-0 rounded-0"
      >
        <div style={styles.fullscreenBody}>
          <div style={styles.fullscreenGrid}>
            <div style={styles.fullscreenPanel}>
              <div style={styles.fullscreenImageWrap}>
                <div style={styles.fullscreenLabel}>
                  <FormattedMessage id="THIS_ENCOUNTER" />
                </div>

                <div style={styles.fullscreenTopRight}>
                  <div
                    style={styles.iconButton}
                    title="Zoom In"
                    onClick={() => fsLeftRef.current?.zoomIn?.()}
                  >
                    <ZoomInIcon />
                  </div>
                  <div
                    style={styles.iconButton}
                    title="Zoom Out"
                    onClick={() => fsLeftRef.current?.zoomOut?.()}
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

            <div style={styles.fullscreenPanel}>
              <div style={styles.fullscreenImageWrap}>
                <div style={styles.fullscreenLabel}>
                  <FormattedMessage id="POSSIBLE_MATCH" />
                </div>

                <div style={styles.fullscreenTopRight}>
                  <div
                    style={styles.iconButton}
                    title="Zoom In"
                    onClick={() => fsRightRef.current?.zoomIn?.()}
                  >
                    <ZoomInIcon />
                  </div>
                  <div
                    style={styles.iconButton}
                    title="Zoom Out"
                    onClick={() => fsRightRef.current?.zoomOut?.()}
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
                        ? "View Hotspotter Visualization"
                        : "No visualization available"
                    }
                    onClick={() => {
                      if (inspectorUrl) {
                        setInspectorOpen(true);
                      }
                    }}
                  >
                    <HatchMarkIcon />
                  </div>
                  <div
                    style={styles.iconButton}
                    title="View Annotations"
                    onClick={() => {
                      fsRightRef.current?.toggleAnnotations?.();
                      fsLeftRef.current?.toggleAnnotations?.();
                    }}
                  >
                    <ToggoleAnnotationIcon />
                  </div>
                  <div
                    style={styles.iconButton}
                    title="Exit fullscreen"
                    onClick={() => setFullscreenOpen(false)}
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
