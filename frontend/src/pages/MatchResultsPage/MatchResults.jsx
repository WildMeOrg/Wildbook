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
import MultiSelectWithCheckbox from "../../components/MultiSelectWithCheckbox";
import ContainerWithSpinner from "../../components/ContainerWithSpinner";

const MatchResults = observer(() => {
  const themeColor = React.useContext(ThemeColorContext);
  const store = useMemo(() => new MatchResultsStore(), []);
  const [instructionsVisible, setInstructionsVisible] = React.useState(false);
  const [params, setParams] = useSearchParams();
  const taskId = params.get("taskId");
  const projectIdPrefix = params.get("projectIdPrefix");
  const { data, isLoading: siteSettingsLoading } = useSiteSettings();

  // Stabilize projectsForUser reference to prevent unnecessary effect re-renders
  const projectsForUser = React.useMemo(
    () => data?.projectsForUser ?? {},
    [data?.projectsForUser],
  );
  const identificationRemarks = React.useMemo(
    () => data?.identificationRemarks ?? [],
    [data?.identificationRemarks],
  );

  const [filterVisible, setFilterVisible] = React.useState(false);
  const [isInputFocused, setIsInputFocused] = React.useState(false);

  const projectOptions = useMemo(() => {
    return Object.entries(projectsForUser).map(([key, value]) => ({
      value: key,
      label: value?.name || key,
    }));
  }, [projectsForUser]);

  useEffect(() => {
    if (taskId) {
      let initialProjectIds = [];

      if (projectIdPrefix) {
        if (siteSettingsLoading) return;

        const match = Object.entries(projectsForUser).find(
          ([, p]) => p?.prefix === projectIdPrefix,
        );
        if (match) {
          initialProjectIds = [match[0]];
        }
      }

      store.setTaskId(taskId);
      store.setProjectNames(initialProjectIds, { fetch: false });
      store.fetchMatchResults();
    } else {
      store.setTaskId(null);
      store.setProjectNames([], { fetch: false });
      store.clearResults();
    }
  }, [taskId, projectIdPrefix, projectsForUser, siteSettingsLoading]);

  useEffect(() => {
    if (!taskId || !store.shouldPoll) return;

    let cancelled = false;

    const scheduleNext = async () => {
      if (cancelled) return;

      await store.fetchMatchResults({ silent: true });

      if (!cancelled && store.shouldPoll) {
        setTimeout(scheduleNext, 5000);
      }
    };

    scheduleNext();

    return () => {
      cancelled = true;
    };
  }, [taskId, store.shouldPoll]);

  if (store.loading) {
    return <FullScreenLoader data-testid="match-results-loader" />;
  }

  const showEmptyState = !store.hasDisplaySections;

  return (
    <Container
      className="mt-2 mb-5"
      id="match-results-page"
      data-testid="match-results-page"
    >
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

      {store.hasResults && store.encounterId && (
        <div data-testid="match-results-bottom-bar">
          <MatchResultsBottomBar
            store={store}
            themeColor={themeColor}
            identificationRemarks={identificationRemarks}
          />
        </div>
      )}

      {store.hasResults && store.encounterId && (
        <div
          style={{ height: "70px" }}
          data-testid="match-results-bottom-bar-spacer"
        />
      )}

      <div
        className="d-flex flex-row justify-content-between align-items-center mb-3"
        id="match-results-header"
        data-testid="match-results-header"
      >
        <div className="d-flex flex-row align-items-center">
          <h2 id="match-results-title" data-testid="match-results-title">
            <FormattedMessage id="MATCH_RESULT" />
          </h2>
        </div>

        <span>
          <div
            title="Match Page Instructions"
            style={{ display: "inline-flex", cursor: "pointer" }}
            id="match-results-instructions-trigger"
            data-testid="match-results-instructions-trigger"
          >
            <InfoIcon
              onClick={() => setInstructionsVisible(true)}
              data-testid="match-results-instructions-icon"
            />
          </div>
        </span>
      </div>

      <div
        className="d-flex align-items-center flex-wrap mb-3"
        id="match-results-toolbar"
        data-testid="match-results-toolbar"
      >
        <div
          className="d-flex align-items-center"
          id="match-results-viewmode"
          data-testid="match-results-viewmode"
        >
          <button
            className="me-2"
            type="button"
            id="match-results-viewmode-individual"
            data-testid="match-results-viewmode-individual"
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
            id="match-results-viewmode-image"
            data-testid="match-results-viewmode-image"
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

        <div
          className="ms-auto d-flex align-items-center flex-wrap"
          id="match-results-controls"
          data-testid="match-results-controls"
        >
          <Form.Group
            className="d-flex align-items-center me-3 mb-2 mb-sm-0"
            id="match-results-num-results-group"
            data-testid="match-results-num-results-group"
          >
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage id="NUMBER_OF_RESULTS" />
            </Form.Label>

            <div
              style={{ position: "relative", width: "80px" }}
              id="match-results-num-results-wrapper"
              data-testid="match-results-num-results-wrapper"
            >
              <Form.Control
                id="match-results-num-results-input"
                data-testid="match-results-num-results-input"
                type="text"
                size="sm"
                value={store.numResults}
                onChange={(e) => {
                  const val = e.target.value;
                  if (/^\d*$/.test(val)) {
                    store.setNumResults(val === "" ? 1 : Number(val));
                  }
                }}
                onFocus={() => setIsInputFocused(true)}
                onBlur={() => setIsInputFocused(false)}
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

              {isInputFocused && (
                <button
                  id="match-results-num-results-apply"
                  data-testid="match-results-num-results-apply"
                  type="button"
                  onClick={() => store.fetchMatchResults()}
                  onMouseDown={(e) => e.preventDefault()}
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
                  aria-label="Apply number of results"
                >
                  ✓
                </button>
              )}
            </div>
          </Form.Group>

          <Form.Group
            className="d-flex align-items-center me-3 mb-2 mb-sm-0"
            id="match-results-project-group"
            data-testid="match-results-project-group"
          >
            <Form.Label className="me-2 mb-0 small">
              <FormattedMessage id="PROJECT" defaultMessage="Project" />
            </Form.Label>

            <div
              style={{ minWidth: "220px", maxWidth: "400px" }}
              id="match-results-project-select-wrapper"
              data-testid="match-results-project-select-wrapper"
            >
              <ContainerWithSpinner loading={siteSettingsLoading}>
                <MultiSelectWithCheckbox
                  options={projectOptions}
                  value={store.projectNames || []}
                  placeholder={
                    <FormattedMessage
                      id="SELECT_PROJECTS"
                      defaultMessage="Select projects"
                    />
                  }
                  onChangeCommitted={(projectIds) => {
                    store.setProjectNames(projectIds);

                    if (!projectIds || projectIds.length === 0) {
                      const next = new URLSearchParams(params);
                      next.delete("projectIdPrefix");
                      setParams(next, { replace: true });
                    }
                  }}
                  style={{ width: "100%" }}
                />
              </ContainerWithSpinner>
            </div>
          </Form.Group>

          <div
            title="Match Criteria"
            style={{
              display: "inline-flex",
              cursor: "pointer",
              marginRight: "10px",
            }}
            id="match-results-filter-trigger"
            data-testid="match-results-filter-trigger"
            onClick={() => setFilterVisible(true)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") setFilterVisible(true);
            }}
            aria-label="Open match criteria"
          >
            <FilterIcon data-testid="match-results-filter-icon" />
          </div>
        </div>
      </div>

      <div id="match-results-content" data-testid="match-results-content">
        {showEmptyState ? (
          <p
            className="mt-3"
            id="match-results-empty"
            data-testid="match-results-empty"
          >
            <FormattedMessage
              id="NO_MATCH_RESULT"
              defaultMessage="No match results available."
            />
          </p>
        ) : (
          <div id="match-results-sections" data-testid="match-results-sections">
            {(store.currentViewData || []).map(
              ({ taskId, columns, metadata }) => (
                <div
                  key={`${store.viewMode}-${taskId}`}
                  id={`match-results-section-${store.viewMode}-${taskId}`}
                  data-testid={`match-results-section-${store.viewMode}-${taskId}`}
                >
                  <MatchProspectTable
                    sectionId={`${store.viewMode}-${taskId}`}
                    taskId={taskId}
                    algorithm={metadata?.algorithm}
                    numCandidates={metadata?.numCandidates}
                    date={metadata?.date}
                    thisEncounterImageUrl={metadata?.queryImageUrl}
                    thisEncounterAnnotations={[
                      metadata?.queryEncounterAnnotation,
                    ]}
                    thisEncounterImageAsset={metadata?.queryEncounterImageAsset}
                    methodName={metadata?.methodName}
                    methodDescription={metadata?.methodDescription}
                    taskStatus={metadata?.taskStatus}
                    taskStatusOverall={metadata?.taskStatusOverall}
                    emptyStateType={metadata?.emptyStateType}
                    errors={metadata?.errors}
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
              ),
            )}
          </div>
        )}
      </div>
    </Container>
  );
});

export default MatchResults;
