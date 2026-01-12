import React, { useMemo, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Form, Modal } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./stores/matchResultsStore";
import MatchProspectTable from "./components/MatchProspectTable";
import MatchResultsBottomBar from "./components/MatchResultsBottomBar";
import { useSearchParams } from "react-router-dom";
import { useSiteSettings } from "../../SiteSettingsContext";
import MainButton from "../../components/MainButton";
import FullScreenLoader from "../../components/FullScreenLoader";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);
  const [params] = useSearchParams();
  const taskId = params.get("taskId");
  const { projectsForUser = {} } = useSiteSettings() || {};

  useEffect(() => {
    if (taskId) {
      store.setTaskId(taskId);
      store.fetchMatchResults();
    }
    return () => {
      // store.resetStore();
    };
  }, [taskId, store]);

  if (store.loading) {
    return <FullScreenLoader />;
  }

  if (!store.hasResults) {
    return (
      <Container className="mt-3 mb-5">
        <h2>
          <FormattedMessage id="MATCH_RESULT" />
        </h2>
        <p className="mt-3">No match results available for this job.</p>
      </Container>
    );
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
        <div className="d-flex flex-row align-items-center">
          <h2>
            <FormattedMessage id="MATCH_RESULT" />
          </h2>
          <a
            href={`/react/encounter?number=${store.encounterId}`}
            className="text-decoration-none"
            target="_blank"
            rel="noopener noreferrer"
          >{` for ${store.encounterId}`}</a>
          {
            store.individualDisplayName && <MainButton
              color="white"
              backgroundColor={themeColor.primaryColors.primary500}
              noArrow
              onClick={() => {
                const url = `/individuals.jsp?id=${store.individualId}`;
                window.open(url, "_blank");
              }}
            >
              {store._individualDisplayName}
            </MainButton>
          }
        </div>
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
            onClick={() => {
              store.setViewMode("individual");
              store.resetSelectionToQuery();
            }}
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
            onClick={() => {
              store.setViewMode("image");
              store.resetSelectionToQuery();
            }}
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
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  store.fetchMatchResults();
                }
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
              onChange={(e) => {
                store.setProjectName(e.target.value);
              }}
              style={{ minWidth: "220px" }}
            >
              <option value="">
                <FormattedMessage id="SELECT_A_PROJECT" />
              </option>
              {/* {Object.entries(projectsForUser).map(([key, value]) => (
                <option key={key} value={key}>
                  {value}
                </option>
              ))} */}
            </Form.Select>
          </Form.Group>
        </div>
      </div>

      {store.currentViewData.map(({ taskId, columns, metadata }) => (
        <div key={`${store.viewMode}-${taskId}`}>
          <MatchProspectTable
            sectionId={`${store.viewMode}-${taskId}`}
            taskId={taskId}
            algorithm={metadata.algorithm}
            numCandidates={metadata.numCandidates}
            date={metadata.date}
            thisEncounterImageUrl={metadata.queryImageUrl}
            methodName={metadata.methodName}
            methodDescription={metadata.methodDescription}
            taskStatus={metadata.taskStatus}
            taskStatusOverall={metadata.taskStatusOverall}
            themeColor={themeColor}
            columns={columns}
            selectedMatch={store.selectedMatch}
            onToggleSelected={(checked, encounterId, individualId) =>
              store.setSelectedMatch(checked, encounterId, individualId)
            }
          />
        </div>
      ))}

      <MatchResultsBottomBar store={store} themeColor={themeColor} />
    </Container>
  );
});

export default MatchResults;
