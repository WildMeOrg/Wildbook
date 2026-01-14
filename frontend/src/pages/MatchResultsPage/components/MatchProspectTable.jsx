import React, { useRef, useState } from "react";
import { Row, Col, Form, Modal } from "react-bootstrap";
import ZoomInIcon from "../icons/ZoomInIcon";
import ZoomOutIcon from "../icons/ZoomOutIcon";
import Icon4 from "../icons/Icon4";
import Icon5 from "../icons/Icon5";
import Icon7 from "../icons/Icon7";
import InteractiveAnnotationOverlay from "../../../components/AnnotationOverlay";
import { FormattedMessage, useIntl } from "react-intl";

const styles = {
  matchRow: (selected, themeColor) => ({
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "6px 10px",
    fontSize: "0.9rem",
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
    fontSize: "0.8rem",
    background: themeColor.wildMeColors.teal100,
    color: themeColor.wildMeColors.teal800,
  }),
  matchImageCard: {
    position: "relative",
    borderRadius: "8px",
    boxShadow: "0 2px 8px rgba(0, 0, 0, 0.15)",
    overflow: "hidden",
    height: "400px",
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
    fontSize: "0.75rem",
    zIndex: 10,
  }),
  toolsBarLeft: {
    position: "absolute",
    top: "50%",
    left: "-40px",
    transform: "translateY(-50%)",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  toolsBarRight: {
    position: "absolute",
    top: "50%",
    right: "-40px",
    transform: "translateY(-50%)",
    display: "flex",
    flexDirection: "column",
    gap: "6px",
  },
  iconButton: {
    width: "32px",
    height: "32px",
    borderRadius: "8px",
    background: "white",
    border: "1px solid #dee2e6",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    boxShadow: "0 1px 4px rgba(0,0,0,0.08)",
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
  fullscreenIconBtn: {
    width: 34,
    height: 34,
    borderRadius: 10,
    background: "rgba(255,255,255,0.92)",
    border: "1px solid rgba(0,0,0,0.10)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    cursor: "pointer",
    boxShadow: "0 2px 10px rgba(0,0,0,0.25)",
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
  columns,
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

  const [selectedRow, setSelectedRow] = useState(() => {
    const first = columns?.[0]?.[0] ?? null;
    if (!first) return null;
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    return { ...first, _rowKey: firstKey };
  });

  React.useEffect(() => {
    const first = columns?.[0]?.[0] ?? null;
    if (!first) {
      setSelectedRow(null);
      return;
    }
    const firstKey = `${first.annotation?.id}-${first.displayIndex}`;
    setSelectedRow({ ...first, _rowKey: firstKey });
  }, [columns]);

  const [hoveredRow, setHoveredRow] = React.useState(null);

  const handleRowClick = (rowData, rowKey) => {
    setSelectedRow({ ...rowData, _rowKey: rowKey });
    rightOverlayRef.current?.reset?.();
  };

  const isSelected = (rowKey) => selectedMatch?.some((d) => d.key === rowKey);

  const rightAnnotations = React.useMemo(() => {
    const ann = selectedRow?.annotation;
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
  }, [selectedRow]);

  const rightImageUrl =
    selectedRow?.annotation?.asset?.url?.replace(
      "http://frontend.scribble.com",
      "https://zebra.wildme.org",
    ) || "";

  const leftOrigW =
    thisEncounterImageAsset?.attributes?.width ??
    thisEncounterImageAsset?.width;
  const leftOrigH =
    thisEncounterImageAsset?.attributes?.height ??
    thisEncounterImageAsset?.height;

  const leftAnnotations = thisEncounterAnnotations;

  const rightOrigW =
    selectedRow?.annotation?.asset?.width ??
    selectedRow?.annotation?.asset?.attributes?.width;
  const rightOrigH =
    selectedRow?.annotation?.asset?.height ??
    selectedRow?.annotation?.asset?.attributes?.height;

  // +++++++++ temporary workaround +++++++++
  const leftImageUrl =
    (thisEncounterImageUrl || "").replace(
      "http://frontend.scribble.com",
      "https://zebra.wildme.org",
    ) || "";

  const openFullscreen = () => {
    setFullscreenOpen(true);
    setTimeout(() => {
      fsLeftRef.current?.reset?.();
      fsRightRef.current?.reset?.();
    }, 0);
  };

  return (
    <div className="mb-4" id={sectionId}>
      <div className="d-flex justify-content-between align-items-center mb-2">
        <div className="d-flex w-100">
          <div style={{ fontWeight: "500" }}>
            {methodName
              ? `${matchesBasedOnText}${" "} ${methodName}`
              : `${matchesBasedOnText}${" "} ${algorithm}`}
          </div>
          <div style={{ marginLeft: "auto", fontWeight: "500" }}>
            <FormattedMessage id="AGAINST" /> {numCandidates}{" "}
            <FormattedMessage id="CANDIDATES" />{" "}
            <span>{date?.slice(0, 16).replace("T", " ")}</span>
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

                const rowKey = `${candidate.annotation?.id}-${candidate.displayIndex}`;
                const isRowSelected = isSelected(rowKey);
                const isRowPreviewed = rowKey === selectedRow?._rowKey;
                const isRowHovered = rowKey === hoveredRow;

                return (
                  <div
                    key={rowKey}
                    onClick={() => handleRowClick(candidate, rowKey)}
                    style={{
                      ...styles.matchRow(isRowSelected, themeColor),
                      cursor: "pointer",
                      backgroundColor:
                        isRowPreviewed || isRowHovered || isRowSelected
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
                      style={{ textDecoration: "none" }}
                      onClick={(e) => e.stopPropagation()}
                    >
                      {(Math.trunc(candidate.score * 10000) / 10000).toFixed(4)}
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
                      {candidateIndividualDisplayName}
                    </button>

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
                showAnnotations
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
                  selectedRow?.annotation?.asset?.rotationInfo ?? null
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
              style={styles.iconButton}
              title="View Hotspotter Visualization"
              onClick={() => {
                if (!selectedRow?.asset?.url) return;
                const url = selectedRow.asset.url;
                window.open(url, "_blank");
              }}
            >
              <Icon4 />
            </div>

            <div
              style={styles.iconButton}
              title="View Annotations"
              onClick={() => rightOverlayRef.current?.toggleAnnotations?.()}
            >
              <Icon5 />
            </div>

            <div
              style={styles.iconButton}
              title="Fullscreen"
              onClick={(e) => {
                e.stopPropagation();
                if (!selectedRow) return;
                openFullscreen();
              }}
            >
              <Icon7 />
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
                  showAnnotations
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
                    style={styles.fullscreenIconBtn}
                    title="Zoom In"
                    onClick={() => fsRightRef.current?.zoomIn?.()}
                  >
                    <ZoomInIcon />
                  </div>
                  <div
                    style={styles.fullscreenIconBtn}
                    title="Zoom Out"
                    onClick={() => fsRightRef.current?.zoomOut?.()}
                  >
                    <ZoomOutIcon />
                  </div>
                  <div
                    style={styles.fullscreenIconBtn}
                    title="View Hotspotter Visualization"
                    onClick={() => {
                      if (!selectedRow?.asset?.url) return;
                      const url = selectedRow.asset.url;
                      window.open(url, "_blank");
                    }}
                  >
                    <Icon4 />
                  </div>
                  <div
                    style={styles.fullscreenIconBtn}
                    title="View Annotations"
                    onClick={() => {
                      fsRightRef.current?.toggleAnnotations?.();
                    }}
                  >
                    <Icon5 />
                  </div>
                  <div
                    style={styles.fullscreenIconBtn}
                    title="Exit fullscreen"
                    onClick={() => setFullscreenOpen(false)}
                  >
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      width="14"
                      height="14"
                      viewBox="0 0 14 14"
                      fill="none"
                    >
                      <path
                        d="M1 11H3V13C3 13.55 3.45 14 4 14C4.55 14 5 13.55 5 13V10C5 9.45 4.55 9 4 9H1C0.45 9 0 9.45 0 10C0 10.55 0.45 11 1 11ZM3 3H1C0.45 3 0 3.45 0 4C0 4.55 0.45 5 1 5H4C4.55 5 5 4.55 5 4V1C5 0.45 4.55 0 4 0C3.45 0 3 0.45 3 1V3ZM10 14C10.55 14 11 13.55 11 13V11H13C13.55 11 14 10.55 14 10C14 9.45 13.55 9 13 9H10C9.45 9 9 9.45 9 10V13C9 13.55 9.45 14 10 14ZM11 3V1C11 0.45 10.55 0 10 0C9.45 0 9 0.45 9 1V4C9 4.55 9.45 5 10 5H13C13.55 5 14 4.55 14 4C14 3.45 13.55 3 13 3H11Z"
                        fill="#00ACCE"
                      />
                    </svg>
                  </div>
                </div>
                <InteractiveAnnotationOverlay
                  ref={fsRightRef}
                  imageUrl={rightImageUrl}
                  originalWidth={rightOrigW}
                  originalHeight={rightOrigH}
                  annotations={rightAnnotations}
                  rotationInfo={
                    selectedRow?.annotation?.asset?.rotationInfo ?? null
                  }
                />
              </div>
            </div>
          </div>
        </div>
      </Modal>
    </div>
  );
};

export default MatchProspectTable;
