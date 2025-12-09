import React, { useMemo, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Form, Modal } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./store/matchResultsStore";
import MatchProspectTable from "./MatchProspectTable";
import MatchResultsBottomBar from "./MatchResultsBottomBar";
import { useSearchParams } from "react-router-dom";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);
  const [params] = useSearchParams();
  const taskId = params.get("taskId");

  useEffect(() => {
    if (taskId) {
      store.setTaskId(taskId);
      store.fetchMatchResults();
    }
    return () => {
      // store.resetStore();
    };
  }, [taskId]);

  if (store.loading) {
    return <p>Loading</p>
  }

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
              onChange={(e) => {
                store.setNumResults(Number(e.target.value));
                store.fetchMatchResults();
              }}
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

      {store.viewMode === "individual" ? [...store.groupedIndivs].map(([algorithmName, data]) => (
        <div key={algorithmName}>
          <MatchProspectTable
            key={store.viewMode}
            algorithm={algorithmName}
            numCandidates={data[0].numberCandidates}
            date={data[0].date}
            thisEncounterImageUrl={data[0].queryEncounterImageAsset?.url}
            themeColor={themeColor}
            candidates={data}
            selectedMatch={store.selectedMatch}
            onToggleSelected={(checked, encounterId, individualId) =>
              store.setSelectedMatch(checked, encounterId, individualId)
            }
            onRowClick={(imageUrl) => store.setPreviewImageUrl(algorithmName, imageUrl)}
            selectedMatchImageUrl={store.getSelectedMatchImageUrl(algorithmName)}
          />
        </div>
      )) : [...store.groupedAnnots].map(([algorithmName, data]) => (
        <div key={algorithmName}>
          <MatchProspectTable
            key={store.viewMode}
            algorithm={algorithmName}
            numCandidates={data[0].numberCandidates}
            date={data[0].date}
            thisEncounterImageUrl={data[0].queryEncounterImageAsset?.url}
            themeColor={themeColor}
            candidates={data}
            selectedMatch={store.selectedMatch}
            onToggleSelected={(checked, encounterId, individualId) =>
              store.setSelectedMatch(checked, encounterId, individualId)
            }
            onRowClick={(imageUrl) => store.setPreviewImageUrl(algorithmName, imageUrl)}
            selectedMatchImageUrl={store.getSelectedMatchImageUrl(algorithmName)}
          />
        </div>
      ))}
      <MatchResultsBottomBar store={store} themeColor={themeColor} />
    </Container>
  );
});

export default MatchResults;