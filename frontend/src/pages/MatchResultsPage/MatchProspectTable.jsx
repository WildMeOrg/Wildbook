import React from "react";
import { Row, Col, Form } from "react-bootstrap";
import { observer } from "mobx-react-lite";
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
  },
  matchImage: {
    width: "100%",
    height: "auto",
    display: "block",
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

const MatchProspectTable = observer(
  ({
    label,
    matchColumns,
    numCandidates,
    evaluatedAt,
    selectedMatch,
    onToggleSelected,
    thisEncounterImageUrl,
    possibleMatchImageUrl,
    themeColor,
  }) => {
    const isSelected = (encounterId) =>
      selectedMatch?.some((d) => d.encounterId === encounterId);

    return (
      <div className="pt-3 mb-4" style={{ borderTop: "1px solid #eee" }}>
        <div className="d-flex justify-content-between align-items-center mb-2">
          <h5 className="mb-0">{label}</h5>
          <div className="small text-muted d-flex align-items-center">
            <span className="me-3">against {numCandidates} candidates</span>
            <span>{evaluatedAt}</span>
          </div>
        </div>

        <div style={styles.matchListScrollContainer}>
          <div style={styles.matchListGrid}>
            {matchColumns.map((column, columnIndex) => (
              <div key={columnIndex} style={styles.matchColumn}>
                {column.map((m) => (
                  <div
                    key={m.id}
                    style={styles.matchRow(isSelected(m.encounterId), themeColor)}
                  >
                    <span style={styles.matchRank}>{m.id}.</span>
                    <span style={styles.matchScore}>
                      {m.score.toFixed(4)}
                    </span>

                    <button
                      type="button"
                      style={styles.idPill(themeColor)}
                      className="btn btn-sm p-0 px-2"
                    >
                      {m.individualId}
                    </button>

                    <div className="ms-auto">
                      <Form.Check
                        type="checkbox"
                        checked={isSelected(m.encounterId)}
                        onChange={(e) =>
                          onToggleSelected(
                            e.target.checked,
                            m.encounterId,
                            m.individualId,
                          )
                        }
                      />
                    </div>
                  </div>
                ))}
              </div>
            ))}
          </div>
        </div>

        <Row>
          <Col
            md={6}
            className="mb-3 mb-md-0"
            style={{ position: "relative" }}
          >
            <div style={styles.matchImageCard}>
              <div style={styles.cornerLabel(themeColor)}>This encounter</div>
              <img
                src={thisEncounterImageUrl}
                alt="This encounter"
                style={styles.matchImage}
              />
            </div>

            <div style={styles.toolsBarLeft}>
              <ZoomInIcon />
              <ZoomOutIcon />
              <Icon3 />
            </div>
          </Col>

          <Col md={6} style={{ position: "relative" }}>
            <div style={styles.matchImageCard}>
              <div style={{ ...styles.cornerLabel(themeColor) }}>
                Possible Match
              </div>
              <img
                src={possibleMatchImageUrl}
                alt="Possible match"
                style={styles.matchImage}
              />
            </div>

            <div style={styles.toolsBarRight}>
              <ZoomInIcon />
              <ZoomOutIcon />
              <Icon3 />
              <Icon5 />
              <Icon7 />
            </div>
          </Col>
        </Row>
      </div>
    );
  },
);

export default MatchProspectTable;