import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Form, Spinner } from "react-bootstrap";
import MainButton from "../../../components/MainButton";

const styles = {
  bottomBar: (themeColor) => ({
    position: "fixed",
    left: 0,
    right: 0,
    bottom: 0,
    background: themeColor.primaryColors.primary50,
    borderTop: "1px solid #dee2e6",
    padding: "10px 24px",
    display: "flex",
    gap: "24px",
    zIndex: 1000,
    height: "70px",
  }),
  bottomText: {
    fontSize: "0.9rem",
  },
  warningText: {
    color: "#dc3545",
    fontSize: "0.9rem",
    fontWeight: "500",
  },
};

const MatchResultsBottomBar = observer(({ store, themeColor }) => {
  const matchingState = store.matchingState;

  const getActionContent = () => {
    switch (matchingState) {
      case "no_individuals": {
        const encId = store.encounterId || "";
        const shortEncId = encId.slice(0, 5);

        const left = (
          <div className="text-truncate" style={{ whiteSpace: "nowrap" }}>
            <FormattedMessage id="SET_MATCH_FOR" />{" "}
            {store.individualDisplayName ? (
              <a
                href={`/individuals.jsp?id=${encodeURIComponent(
                  store.individualId,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-decoration-none"
              >
                {store.individualDisplayName}
              </a>
            ) : (
              <a
                href={`/react/encounter?number=${encodeURIComponent(encId)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-decoration-none"
              >
                {shortEncId}
              </a>
            )}{" "}
            <FormattedMessage id="OR" />
          </div>
        );

        const right = (
          <>
            <Form.Control
              type="text"
              placeholder="New Individual Name"
              value={store.newIndividualName}
              onChange={(e) => store.setNewIndividualName(e.target.value)}
              size="sm"
              style={{ width: 280 }}
            />
            <MainButton
              noArrow
              backgroundColor={themeColor.primaryColors.primary500}
              color="white"
              onClick={store.handleConfirmNoMatch}
              disabled={
                !String(store.newIndividualName || "").trim() ||
                store.matchRequestLoading
              }
              style={{ marginTop: 0, marginBottom: 0 }}
            >
              <FormattedMessage id="MARK_AS_NEW_INDIVIDUAL" />
              {store.matchRequestLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  role="status"
                  aria-hidden="true"
                  className="ms-2"
                />
              )}
            </MainButton>
          </>
        );

        return { left, right };
      }

      case "single_individual": {
        const all = store.selectedIncludingQuery || [];
        const individualItem = all.find((x) => x?.individualId);

        const individualName =
          (individualItem?.encounterId === store.encounterId
            ? store.individualDisplayName
            : null) ||
          individualItem?.individualDisplayName ||
          individualItem?.individualId ||
          "";

        const encounterNum = all.filter(
          (x) => x?.encounterId && !x?.individualId,
        ).length;

        return {
          left: (
            <div className="text-truncate" style={{ whiteSpace: "nowrap" }}>
              <FormattedMessage id="MERGE_INDIVIDUAL" />{" "}
              <a
                href={`/individuals.jsp?id=${encodeURIComponent(
                  individualItem?.individualId,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-decoration-none"
              >
                {individualName}
              </a>
              {encounterNum > 0 && (
                <>
                  {" "}
                  <FormattedMessage
                    id="AND_N_ENCOUNTERS"
                    values={{ count: encounterNum }}
                  />
                </>
              )}
            </div>
          ),
          right: (
            <MainButton
              noArrow
              backgroundColor={themeColor.primaryColors.primary500}
              color="white"
              onClick={store.handleMatch}
              disabled={store.matchRequestLoading}
              style={{ marginTop: 0, marginBottom: 0 }}
            >
              <FormattedMessage id="CONFIRM_MATCH" />
              {store.matchRequestLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  role="status"
                  aria-hidden="true"
                  className="ms-2"
                />
              )}
            </MainButton>
          ),
        };
      }

      case "two_individuals": {
        const all = store.selectedIncludingQuery || [];
        const individualsRaw = all.filter((x) => x?.individualId);
        const individuals = Array.from(
          new Map(individualsRaw.map((x) => [x.individualId, x])).values(),
        );

        individuals.sort((x) =>
          x?.encounterId === store.encounterId ? -1 : 1,
        );

        const a = individuals[0];
        const b = individuals[1];

        const nameA = (a?.encounterId === store.encounterId
          ? store.individualDisplayName
          : null) ||
          a?.individualDisplayName ||
          a?.individualId || <FormattedMessage id="INDIVIDUAL_A" />;

        const nameB = (b?.encounterId === store.encounterId
          ? store.individualDisplayName
          : null) ||
          b?.individualDisplayName ||
          b?.individualId || <FormattedMessage id="INDIVIDUAL_B" />;

        const encounters = all.filter(
          (x) => x?.encounterId && !x?.individualId,
        );

        return {
          left: (
            <div className="text-truncate" style={{ whiteSpace: "nowrap" }}>
              <FormattedMessage id="MERGE" />{" "}
              <a
                href={`/individuals.jsp?id=${encodeURIComponent(
                  a?.individualId,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-decoration-none"
              >
                {nameA}
              </a>{" "}
              <FormattedMessage id="AND" />{" "}
              <a
                href={`/individuals.jsp?id=${encodeURIComponent(
                  b?.individualId,
                )}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-decoration-none"
              >
                {nameB}
              </a>
              {encounters.length > 0 && (
                <>
                  {" "}
                  <FormattedMessage
                    id="AND_N_ENCOUNTERS"
                    values={{ count: encounters.length }}
                  />
                </>
              )}
            </div>
          ),
          right: (
            <MainButton
              color="white"
              backgroundColor={themeColor.primaryColors.primary700}
              noArrow
              onClick={store.handleMerge}
              disabled={store.matchRequestLoading}
              style={{ marginTop: 0, marginBottom: 0 }}
            >
              <FormattedMessage id="MERGE_INDIVIDUALS" />
              {store.matchRequestLoading && (
                <Spinner
                  animation="border"
                  size="sm"
                  role="status"
                  aria-hidden="true"
                  className="ms-2"
                />
              )}
            </MainButton>
          ),
        };
      }

      case "too_many_individuals":
        return {
          left: (
            <div
              className="text-truncate"
              style={{ ...styles.warningText, whiteSpace: "nowrap" }}
            >
              <FormattedMessage id="CANNOT_MERGE_MORE_THAN_TWO" />
            </div>
          ),
          right: null,
        };

      case "no_further_action_needed":
        if (store.selectedMatch.length === 0) {
          const encId = store.encounterId || "";
          const shortEncId = encId.slice(0, 5);

          return {
            left: (
              <div className="text-truncate" style={{ whiteSpace: "nowrap" }}>
                <FormattedMessage id="SET_MATCH_FOR" />{" "}
                {store.individualDisplayName ? (
                  <a
                    href={`/individuals.jsp?id=${encodeURIComponent(
                      store.individualId,
                    )}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-decoration-none"
                  >
                    {store.individualDisplayName}
                  </a>
                ) : (
                  <a
                    href={`/react/encounter?number=${encodeURIComponent(encId)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-decoration-none"
                  >
                    {shortEncId}
                  </a>
                )}
              </div>
            ),
            right: null,
          };
        }

        return {
          left: (
            <div className="text-truncate" style={{ whiteSpace: "nowrap" }}>
              <FormattedMessage id="NO_FURTHER_ACTION_NEEDED" />
            </div>
          ),
          right: null,
        };

      default:
        return { left: null, right: null };
    }
  };

  const { left, right } = getActionContent();

  return (
    <div style={styles.bottomBar(themeColor)}>
      <div
        className="d-flex align-items-center w-100"
        style={{ marginLeft: 20, marginRight: 20 }}
      >
        <div
          className="me-3 flex-grow-1 text-truncate"
          style={{ whiteSpace: "nowrap", minWidth: 0 }}
        >
          {left}
        </div>

        <div
          className="ms-auto d-flex align-items-center flex-nowrap"
          style={{ gap: 12 }}
        >
          {right}
          <MainButton
            noArrow
            backgroundColor="white"
            color={themeColor.primaryColors.primary500}
            onClick={() => window.close()}
            style={{ marginTop: 0, marginBottom: 0 }}
          >
            <FormattedMessage id="CANCEL" />
          </MainButton>
        </div>
      </div>
    </div>
  );
});

export default MatchResultsBottomBar;
