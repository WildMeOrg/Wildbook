import React from "react";
import { Row, Col, Form } from "react-bootstrap";
import ZoomInIcon from "./icons/ZoomInIcon";
import ZoomOutIcon from "./icons/ZoomOutIcon";
import Icon3 from "./icons/Icon3";
import Icon5 from "./icons/Icon5";
import Icon7 from "./icons/Icon7";

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
  },
  matchScore: {
    width: "64px",
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
  matchImage: {
    width: "100%",
    height: "100% ",
    display: "block",
    objectFit: "contain",
    backgroundColor: "#f8f9fa",
    transformOrigin: "center center",
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
    zIndex: 1000,
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
};

const MatchProspectTable =
  ({
    key,
    numCandidates,
    date,
    selectedMatch,
    onToggleSelected = {},
    thisEncounterImageUrl,
    themeColor,
    candidates,
    algorithm,
  }) => {
    const [previewedEncounterId, setPreviewedEncounterId] = React.useState(candidates[0].annotation?.encounter.id);
    const [selectedMatchImageUrl, setSelectedMatchImageUrl] = React.useState(candidates[0].annotation?.asset.url);
    const [leftImageZoom, setLeftImageZoom] = React.useState(1);
    const [rightImageZoom, setRightImageZoom] = React.useState(1);
    const [leftPanEnabled, setLeftPanEnabled] = React.useState(false);
    const [rightPanEnabled, setRightPanEnabled] = React.useState(false);
    const [leftPanPosition, setLeftPanPosition] = React.useState({ x: 0, y: 0 });
    const [rightPanPosition, setRightPanPosition] = React.useState({ x: 0, y: 0 });
    const [isDragging, setIsDragging] = React.useState(null);
    const [dragStart, setDragStart] = React.useState({ x: 0, y: 0 });

    const handleZoomIn = (side) => {
      if (side === "left") {
        setLeftImageZoom((prev) => Math.min(prev + 0.25, 3));
      } else {
        setRightImageZoom((prev) => Math.min(prev + 0.25, 3));
      }
    };

    const handleZoomOut = (side) => {
      if (side === "left") {
        setLeftImageZoom((prev) => Math.max(prev - 0.25, 0.5));
      } else {
        setRightImageZoom((prev) => Math.max(prev - 0.25, 0.5));
      }
    };

    const handleResetZoom = (side) => {
      if (side === "left") {
        setLeftImageZoom(1);
      } else {
        setRightImageZoom(1);
      }
    };

    const togglePanMode = (side) => {
      if (side === "left") {
        setLeftPanEnabled((prev) => !prev);
        if (leftPanEnabled) {
          setLeftPanPosition({ x: 0, y: 0 });
        }
      } else {
        setRightPanEnabled((prev) => !prev);
        if (rightPanEnabled) {
          setRightPanPosition({ x: 0, y: 0 });
        }
      }
    };

    const handleMouseDown = (side, e) => {
      const panEnabled = side === "left" ? leftPanEnabled : rightPanEnabled;
      if (!panEnabled) return;

      setIsDragging(side);
      setDragStart({
        x: e.clientX,
        y: e.clientY,
      });
    };

    const handleMouseMove = (e) => {
      if (!isDragging) return;

      const deltaX = e.clientX - dragStart.x;
      const deltaY = e.clientY - dragStart.y;

      if (isDragging === "left") {
        setLeftPanPosition((prev) => ({
          x: prev.x + deltaX,
          y: prev.y + deltaY,
        }));
      } else {
        setRightPanPosition((prev) => ({
          x: prev.x + deltaX,
          y: prev.y + deltaY,
        }));
      }

      setDragStart({
        x: e.clientX,
        y: e.clientY,
      });
    };

    const handleMouseUp = () => {
      setIsDragging(null);
    };

    const handleRowClick = (encounterId, imageUrl) => {
      setPreviewedEncounterId(encounterId);
      setSelectedMatchImageUrl(imageUrl);
    };

    React.useEffect(() => {
      if (isDragging) {
        window.addEventListener("mousemove", handleMouseMove);
        window.addEventListener("mouseup", handleMouseUp);
        return () => {
          window.removeEventListener("mousemove", handleMouseMove);
          window.removeEventListener("mouseup", handleMouseUp);
        };
      }
    }, [isDragging, dragStart]);

    const isSelected = (encounterId) =>
      selectedMatch?.some((d) => d.encounterId === encounterId);

    return (
      <div className="mb-4" id={`${key}-${algorithm}`}>
        <div className="d-flex justify-content-between align-items-center mb-2">
          <div className="small text-muted d-flex">
            <div>{algorithm}</div>
            <div
              style={{ marginLeft: "auto" }}
            >against {numCandidates} candidates <span>{date}</span></div>
          </div>
        </div>

        <div style={styles.matchListScrollContainer}>
          <div style={styles.matchListGrid}>
            {candidates.map((candidate, columnIndex) => {
              const candidateEncounterId = candidate.annotation?.encounter?.id;
              const candidateIndividualId = candidate.annotation?.individual?.id;
              const candidateIndividualDisplayName = candidate.annotation?.individual?.displayName;
              const candidateImageUrl = candidate.annotation?.asset?.url;
              const isRowSelected = isSelected(candidateEncounterId);
              const isRowPreviewed = candidateEncounterId === previewedEncounterId;
              return <div key={columnIndex} style={styles.matchColumn}>
                <div
                  key={candidateEncounterId}
                  style={{
                    ...styles.matchRow(isRowSelected, themeColor),
                    cursor: "pointer",
                    backgroundColor: isRowPreviewed
                      ? themeColor.primaryColors.primary50
                      : "transparent",
                  }}
                  onClick={() => handleRowClick(candidateEncounterId, candidateImageUrl)}
                >
                  <span style={styles.matchScore}>
                    {candidate.score.toFixed(4)}
                  </span>
                  <button
                    type="button"
                    style={styles.idPill(themeColor)}
                    className="btn btn-sm p-0 px-2"
                  >
                    {candidateIndividualDisplayName}
                  </button>
                  <div
                    className="ms-auto"
                    onClick={(e) => e.stopPropagation()}
                  >
                    <Form.Check
                      type="checkbox"
                      checked={isRowSelected}
                      onChange={(e) =>
                        onToggleSelected(
                          e.target.checked,
                          candidateEncounterId,
                          candidateIndividualDisplayName,
                        )
                      }
                    />
                  </div>
                </div>
              </div>
            })}
          </div>
        </div>

        <Row>
          <Col md={6} className="mb-3 mb-md-0" style={{ position: "relative" }}>
            <div style={styles.matchImageCard}>
              <div style={styles.cornerLabel(themeColor)}>This encounter</div>
              <div
                style={{
                  ...styles.imageContainer,
                  cursor: leftPanEnabled ? "grab" : "default",
                }}
                onMouseDown={(e) => handleMouseDown("left", e)}
              >
                <img
                  src={thisEncounterImageUrl}
                  alt="This encounter"
                  style={{
                    ...styles.matchImage,
                    transform: `scale(${leftImageZoom}) translate(${leftPanPosition.x}px, ${leftPanPosition.y}px)`,
                    transition: isDragging === "left" ? "none" : "transform 0.2s ease",
                    cursor: leftPanEnabled ? (isDragging === "left" ? "grabbing" : "grab") : "default",
                  }}
                  draggable={false}
                />
              </div>
            </div>

            <div style={styles.toolsBarLeft}>
              <div
                onClick={() => handleZoomIn("left")}
                style={styles.iconButton}
                title="Zoom In"
              >
                <ZoomInIcon />
              </div>
              <div
                onClick={() => handleZoomOut("left")}
                style={styles.iconButton}
                title="Zoom Out"
              >
                <ZoomOutIcon />
              </div>
              <div
                onClick={() => togglePanMode("left")}
                style={{
                  ...styles.iconButton,
                  backgroundColor: leftPanEnabled
                    ? themeColor.primaryColors.primary200
                    : "white",
                }}
                title="Pan Image (Click to toggle)"
              >
                <Icon3 />
              </div>
            </div>
          </Col>

          <Col md={6} style={{ position: "relative" }}>
            <div style={styles.matchImageCard}>
              <div style={{ ...styles.cornerLabel(themeColor) }}>
                Possible Match
              </div>
              <div
                style={{
                  ...styles.imageContainer,
                  cursor: rightPanEnabled ? "grab" : "default",
                }}
                onMouseDown={(e) => handleMouseDown("right", e)}
              >
                <img
                  src={selectedMatchImageUrl}
                  alt="Possible match"
                  style={{
                    ...styles.matchImage,
                    transform: `scale(${rightImageZoom}) translate(${rightPanPosition.x}px, ${rightPanPosition.y}px)`,
                    transition: isDragging === "right" ? "none" : "transform 0.2s ease",
                    cursor: rightPanEnabled ? (isDragging === "right" ? "grabbing" : "grab") : "default",
                  }}
                  draggable={false}
                />
              </div>
            </div>

            <div style={styles.toolsBarRight}>
              <div
                onClick={() => handleZoomIn("right")}
                style={styles.iconButton}
                title="Zoom In"
              >
                <ZoomInIcon />
              </div>
              <div
                onClick={() => handleZoomOut("right")}
                style={styles.iconButton}
                title="Zoom Out"
              >
                <ZoomOutIcon />
              </div>
              <div
                onClick={() => togglePanMode("right")}
                style={{
                  ...styles.iconButton,
                  backgroundColor: rightPanEnabled
                    ? themeColor.primaryColors.primary200
                    : "white",
                }}
                title="Pan Image (Click to toggle)"
              >
                <Icon3 />
              </div>
              <div style={styles.iconButton}>
                <Icon5 />
              </div>
              <div style={styles.iconButton}>
                <Icon7 />
              </div>
            </div>
          </Col>
        </Row>
      </div>
    );
  }

export default MatchProspectTable;