import React, { useMemo } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Form, Modal } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./store/matchResultsStore";
import MatchProspectTable from "./MatchProspectTable";
import MatchResultsBottomBar from "./MatchResultsBottomBar";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);

  return (
    <Container className="mt-3 mb-5">
      <Modal
        show={instructionsVisible}
        size="lg"
        onHide={() => setInstructionsVisible(false)}
      >
        <Modal.Header closeButton onHide={() => setInstructionsVisible(false)}>
          <Modal.Title>
            <FormattedMessage id="MATCHING_PAGE_INSTRUCTIONS" />
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <p>
            <FormattedMessage id="MATCH_RESULT_INSTRUCTIONS_TEXT" />
          </p>
        </Modal.Body>
      </Modal>

      <div className="d-flex flex-row justify-content-between align-items-center mb-3">
        <h2>
          <FormattedMessage id="MATCH_RESULT" />
        </h2>
        <span>
          <i
            className="bi bi-info-circle-fill"
            style={{
              cursor: "pointer",
              color: themeColor.primaryColors.primary500,
              fontSize: "1.6rem",
            }}
            title="Help"
            onClick={() => setInstructionsVisible(true)}
          ></i>
        </span>
      </div>

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
                  : themeColor.primaryColors.primary50,
              padding: "5px 10px",
              border: "none",
              color:
                store.viewMode === "image"
                  ? "white"
                  : themeColor.primaryColors.primary700,
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
            <Form.Control
              type="number"
              size="sm"
              min="1"
              value={store.numResults}
              onChange={(e) => store.setNumResults(Number(e.target.value))}
              style={{ width: "80px" }}
            />
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
        </div>
      </div>

      {store.algorithms.map((algo) => {
        const matchColumns = store.organizeMatchesIntoColumns(algo.matches);

        return (
          <MatchProspectTable
            key={algo.id}
            algorithmId={algo.id}  
            label={algo.label}
            matchColumns={matchColumns}
            numCandidates={store.numCandidates}
            evaluatedAt={store.evaluatedAt}
            selectedMatch={store.selectedMatch}
            onToggleSelected={(checked, encounterId, individualId) =>
              store.setSelectedMatch(checked, encounterId, individualId)
            }
            onRowClick={(imageUrl) => store.setPreviewImageUrl(algo.id, imageUrl)}
            thisEncounterImageUrl={store.thisEncounterImageUrl}
            selectedMatchImageUrl={store.getSelectedMatchImageUrl(algo.id)}  
            themeColor={themeColor}
          />
        );
      })}

      <MatchResultsBottomBar store={store} themeColor={themeColor} />
    </Container>
  );
});

export default MatchResults;