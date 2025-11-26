import React, { useMemo } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Row, Col, Form, Button } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./store/matchResultsStore";
import ZoomInIcon from "./icons/ZoomInIcon";
import ZoomOutIcon from "./icons/ZoomOutIcon";
import Icon3 from "./icons/Icon3";
import Icon4 from "./icons/Icon4";
import Icon5 from "./icons/Icon5";
import Icon6 from "./icons/Icon6";
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
  idPillOutline: {
    background: "transparent",
    border: "1px solid #ccc",
  },
  matchImageCard: {
    position: "relative",
    borderRadius: "8px",
    // overflow: "hidden",
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
  toolBtn: {
    width: "32px",
    height: "32px",
    borderRadius: "16px",
    border: "none",
    background: "#ffffff",
    boxShadow: "0 1px 4px rgba(0, 0, 0, 0.2)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    fontSize: "16px",
  },
  bottomBar: {
    position: "fixed",
    left: 0,
    right: 0,
    bottom: 0,
    background: "#f8f9fa",
    borderTop: "1px solid #dee2e6",
    padding: "10px 24px",
    display: "flex",
    justifyContent: "center",
    gap: "24px",
    alignItems: "center",
    zIndex: 1000,
  },
  bottomText: {
    fontSize: "0.9rem",
  },
};

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);

  return (
    <Container className="mt-3 mb-5">
      <h2 className="mb-3">
        <FormattedMessage id="MATCH_RESULT" />
      </h2>

      <div className="d-flex align-items-center flex-wrap mb-3">
        <div className="d-flex align-items-center">
          <button
            className="me-2"
            type="button"
            style={{
              borderRadius: "35px",
              backgroundColor:
                store.viewMode === "individual"
                  ? themeColor.primaryColors.primary500
                  : "white",
              border: "none",
              padding: "5px 10px",
              color:
                store.viewMode === "individual"
                  ? "white"
                  : themeColor.primaryColors.primary500,
            }}
            onClick={() => store.setViewMode("individual")}
          >
            <FormattedMessage id="INDIVIDUAL_SCORE" />
          </button>

          <button
            type="button"
            style={{
              borderRadius: "35px",
              backgroundColor:
                store.viewMode === "image"
                  ? themeColor.primaryColors.primary500
                  : themeColor.primaryColors.primary100,
              padding: "5px 10px",
              border: "none",
              color:
                store.viewMode === "image"
                  ? "white"
                  : themeColor.primaryColors.primary500,
            }}
            onClick={() => store.setViewMode("image")}
          >
            <FormattedMessage id="IMAGE_SCORE" />
          </button>
        </div>

        <div className="ms-auto d-flex align-items-center flex-wrap">
          <Form.Group className="d-flex align-items-center me-3 mb-2 mb-sm-0">
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage
                id="NUMBER_OF_RESULTS"
                defaultMessage="Number of Results"
              />
            </Form.Label>
            <Form.Select
              size="sm"
              value={store.numResults}
              onChange={(e) => store.setNumResults(Number(e.target.value))}
              style={{ width: "80px" }}
            >
              {[4, 8, 12, 16].map((n) => (
                <option key={n} value={n}>
                  {n}
                </option>
              ))}
            </Form.Select>
          </Form.Group>

          <Form.Group className="d-flex align-items-center me-3 mb-2 mb-sm-0">
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage id="PROJECT" defaultMessage="Project" />
            </Form.Label>
            <Form.Select
              size="sm"
              value={store.projectName}
              onChange={(e) => store.setProjectName(e.target.value)}
              style={{ minWidth: "220px" }}
            >
              <option value={store.projectName}>{store.projectName}</option>
            </Form.Select>
          </Form.Group>

          <div className="small text-muted d-flex align-items-center mb-2 mb-sm-0">
            {store.evaluatedAt}
            <span>
              <i className="bi bi-info-circle"></i>
            </span>
          </div>
        </div>
      </div>

      {store.algorithms.map((algo) => {
        const half = Math.ceil(algo.matches.length / 2);
        const leftMatches = algo.matches.slice(0, half);
        const rightMatches = algo.matches.slice(half);

        return (
          <div
            key={algo.id}
            className="pt-3 mb-4"
            style={{ borderTop: "1px solid #eee" }}
          >
            <div className="d-flex justify-content-between align-items-center mb-2">
              <h5 className="mb-0">{algo.label}</h5>
              <div className="small text-muted d-flex align-items-center">
                <span className="me-3">
                  against {store.numCandidates} candidates
                </span>
                <span>{store.evaluatedAt}</span>
              </div>
            </div>

            <Row className="mb-3">
              <Col md={6} className="mb-3 mb-md-0">
                <div>
                  {leftMatches.map((m) => (
                    <div
                      key={m.rank}
                      style={styles.matchRow(
                        store.selectedMatch?.some(
                          (data) => data.encounterId === m.encounterId,
                        ),
                        themeColor,
                      )}
                    >
                      <span style={styles.matchRank}>{m.rank}.</span>
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
                          checked={store.selectedMatch?.some(
                            (data) => data.encounterId === m.encounterId,
                          )}
                          onChange={(e) =>
                            store.setSelectedMatch(
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
              </Col>

              <Col md={6}>
                <div style={styles.matchListColumn}>
                  {rightMatches.map((m) => (
                    <div
                      key={m.rank}
                      style={styles.matchRow(
                        store.selectedMatch?.some(
                          (data) => data.encounterId === m.encounterId,
                        ),
                        themeColor,
                      )}
                    >
                      <span style={styles.matchRank}>{m.rank}.</span>
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
                          checked={store.selectedMatch?.some(
                            (data) => data.encounterId === m.encounterId,
                          )}
                          onChange={(e) =>
                            store.setSelectedMatch(
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
              </Col>
            </Row>

            <Row>
              <Col
                md={6}
                className="mb-3 mb-md-0"
                style={{ position: "relative" }}
              >
                <div style={styles.matchImageCard}>
                  <div style={styles.cornerLabel(themeColor)}>
                    This encounter
                  </div>
                  <img
                    src={store.thisEncounterImageUrl}
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
                  <div
                    style={{
                      ...styles.cornerLabel(themeColor),
                    }}
                  >
                    Possible Match
                  </div>
                  <img
                    src={
                      store.selectedMatchImageUrl || store.thisEncounterImageUrl
                    }
                    alt="Possible match"
                    style={styles.matchImage}
                  />
                </div>

                <div style={styles.toolsBarRight}>
                  <ZoomInIcon />
                  <ZoomOutIcon />
                  <Icon3 />
                  <Icon4 />
                  <Icon5 />
                  <Icon6 />
                  <Icon7 />
                </div>
              </Col>
            </Row>
          </div>
        );
      })}

      {store.selectedIndividualId && (
        <div style={styles.bottomBar}>
          <div style={styles.bottomText}>
            Encounter{" "}
            <span
              style={{
                ...styles.idPill(themeColor),
                ...styles.idPillOutline,
                marginRight: "4px",
              }}
            >
              {store.encounterId}
            </span>
            {" is a match to Individual "}
            <span style={styles.idPill(themeColor)}>
              {store.selectedIndividualId}
            </span>
          </div>
          <div className="d-flex align-items-center">
            <Button variant="primary" size="sm">
              <FormattedMessage
                id="CONFIRM_MATCH"
                defaultMessage="Confirm Match"
              />
            </Button>
            <Button variant="outline-secondary" size="sm" className="ms-2">
              <FormattedMessage
                id="MARK_AS_NEW_INDIVIDUAL"
                defaultMessage="Mark as New Individual"
              />
            </Button>
          </div>
        </div>
      )}
    </Container>
  );
});

export default MatchResults;
