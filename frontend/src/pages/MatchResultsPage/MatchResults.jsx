import React, { useMemo, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Container, Form } from "react-bootstrap";
import ThemeColorContext from "../../ThemeColorProvider";
import MatchResultsStore from "./stores/matchResultsStore";
import MatchProspectTable from "./components/MatchProspectTable";
import MatchResultsBottomBar from "./components/MatchResultsBottomBar";
import { useSearchParams } from "react-router-dom";
import { useSiteSettings } from "../../SiteSettingsContext";
import FullScreenLoader from "../../components/FullScreenLoader";
import InstructionsModal from "./components/InstructionsModal";
import InfoIcon from "./icons/InfoIcon";
import FilterIcon from "./icons/FilterIcon";
import MatchCriteriaDrawer from "./components/MatchCriteriaDrawer";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);
  const [params] = useSearchParams();
  const taskId = params.get("taskId");
  const projectIdPrefix = params.get("projectIdPrefix");
  const { projectsForUser = {}, identificationRemarks = [] } =
    useSiteSettings() || {};
  const [filterVisible, setFilterVisible] = React.useState(false);

  useEffect(()=> {
    if(!projectIdPrefix) return;
    store.setProjectName(projectIdPrefix);
  }, [projectIdPrefix])

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

  return (
    <Container className="mt-2 mb-5">
      <InstructionsModal
        show={instructionsVisible}
        onHide={() => setInstructionsVisible(false)}
        taskId={taskId}
        themeColor={themeColor}
      />

      <MatchCriteriaDrawer
        show={filterVisible}
        onHide={() => setFilterVisible(false)}
        filter={store.matchingSetFilter}
      />

      {store.hasResults && (
        <MatchResultsBottomBar
          store={store}
          themeColor={themeColor}
          identificationRemarks={identificationRemarks}
        />
      )}

      {store.hasResults && <div style={{ height: "70px" }} />}

      <div className="d-flex flex-row justify-content-between align-items-center mb-3">
        <div className="d-flex flex-row align-items-center">
          <h2>
            <FormattedMessage id="MATCH_RESULT" />
          </h2>
        </div>
        <span>
          <div
            title="Match Page Instructions"
            style={{ display: "inline-flex", cursor: "pointer" }}
          >
            <InfoIcon onClick={() => setInstructionsVisible(true)} />
          </div>
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
                  : themeColor.primaryColors.primary50,
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
              <FormattedMessage id="NUMBER_OF_RESULTS" />
            </Form.Label>
            <div style={{ position: "relative", width: "80px" }}>
              <Form.Control
                type="text"
                size="sm"
                value={store.numResults}
                onChange={(e) => {
                  const val = e.target.value;
                  if (/^\d*$/.test(val)) {
                    store.setNumResults(val === "" ? 1 : Number(val));
                  }
                }}
                onKeyDown={(e) => {
                  if (e.key === "Enter") {
                    store.fetchMatchResults();
                  }
                }}
                style={{
                  width: "100%",
                  paddingRight: "30px",
                }}
              />
              <button
                type="button"
                onClick={() => store.fetchMatchResults()}
                style={{
                  position: "absolute",
                  right: "4px",
                  top: "50%",
                  transform: "translateY(-50%)",
                  border: "none",
                  background: "transparent",
                  cursor: "pointer",
                  padding: "0 4px",
                  fontSize: "16px",
                  color: themeColor.primaryColors.primary500,
                  lineHeight: "1",
                }}
                title="Apply changes"
              >
                ✓
              </button>
            </div>
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
              style={{ minWidth: "220px", maxWidth: "400px" }}
            >
              <option value="">
                <FormattedMessage id="SELECT_A_PROJECT" />
              </option>
              {Object.entries(projectsForUser).map(([key, value]) => (
                <option key={key} value={key}>
                  {value?.name}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
          <div
            title="Match Criteria"
            style={{
              display: "inline-flex",
              cursor: "pointer",
              marginRight: "10px",
            }}
            onClick={() => setFilterVisible(true)}
          >
            <FilterIcon />
          </div>
        </div>
      </div>
      {!store.hasResults ? (
        <p className="mt-3">No match results available for this job.</p>
      ) : (
        store.currentViewData.map(({ taskId, columns, metadata }) => (
          <div key={`${store.viewMode}-${taskId}`}>
            <MatchProspectTable
              sectionId={`${store.viewMode}-${taskId}`}
              taskId={taskId}
              algorithm={metadata.algorithm}
              numCandidates={metadata.numCandidates}
              date={metadata.date}
              thisEncounterImageUrl={metadata.queryImageUrl}
              thisEncounterAnnotations={[metadata.queryEncounterAnnotation]}
              thisEncounterImageAsset={metadata.queryEncounterImageAsset}
              methodName={metadata.methodName}
              methodDescription={metadata.methodDescription}
              taskStatus={metadata.taskStatus}
              taskStatusOverall={metadata.taskStatusOverall}
              themeColor={themeColor}
              columns={columns}
              selectedMatch={store.selectedMatch}
              onToggleSelected={(
                checked,
                key,
                encounterId,
                individualId,
                individualDisplayName,
              ) => {
                store.setSelectedMatch(
                  checked,
                  key,
                  encounterId,
                  individualId,
                  individualDisplayName,
                );
              }}
            />
          </div>
        ))
      )}
    </Container>
  );
});

export default MatchResults;
