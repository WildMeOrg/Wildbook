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
  }),
  bottomText: {
    fontSize: "0.9rem",
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
  warningText: {
    color: "#dc3545",
    fontSize: "0.9rem",
    fontWeight: "500",
  },
};

const MatchResultsBottomBar = observer(({ store, themeColor }) => {

  const renderActions = () => {
    const matchingState = store.matchingState;

    switch (matchingState) {
      case "no_selection":
        return (
          <>
            <Form.Control
              type="text"
              placeholder="New Individual Name"
              value={store.newIndividualName}
              onChange={(e) => store.setNewIndividualName(e.target.value)}
              style={{ maxWidth: "300px" }}
              size="sm"
            />

            <MainButton
              noArrow={true}
              backgroundColor={themeColor.primaryColors.primary500}
              color="white"
              onClick={store.handleConfirmNoMatch}
              disabled={(!store.newIndividualName || "").trim() || !!store.individualId}
              style={{ marginTop: "0", marginBottom: "0" }}
            >
              <FormattedMessage
                id="CONFIRM_NO_MATCH"
              />
            </MainButton>
          </>
        );

      case "no_individuals":
        return (
          <>
            <Form.Control
              type="text"
              placeholder="New Individual Name"
              value={store.newIndividualName}
              onChange={(e) => store.setNewIndividualName(e.target.value)}
              style={{ maxWidth: "300px" }}
              size="sm"
            />

            <MainButton
              noArrow={true}
              backgroundColor={themeColor.primaryColors.primary500}
              color="white"
              onClick={() => { }}
              disabled={!store.newIndividualName.trim()}
              style={{ marginTop: "0", marginBottom: "0" }}
            >
              <FormattedMessage
                id="CONFIRM_NO_MATCH"
              />
            </MainButton>
          </>
        );

      //don't forget another case: 
      //All encounters already assigned to the same individual ID. No further action is needed to confirm this match.

      case "single_individual":
        return (
          <MainButton
            noArrow={true}
            backgroundColor={themeColor.primaryColors.primary500}
            color="white"
            onClick={async () => {
              const data = await store.handleMatch();
              console.log("match response:", data);
            }}
            disabled={(!store.individualId && !store.selectedMatch.some(data => data.individualId)) || store.matchRequestLoading}
            style={{ marginTop: "0", marginBottom: "0" }}
          >
            <FormattedMessage
              id="CONFIRM_MATCH"
              defaultMessage="Confirm Match"
            />
            {store.matchRequestLoading && <Spinner
              animation="border"
              size="sm"
              role="status"
              aria-hidden="true"
              className="ms-2"
            />}
          </MainButton>
        );

      case "two_individuals":
        return (
          <MainButton
            color="white"
            backgroundColor={themeColor.primaryColors.primary700}
            noArrow
            onClick={() => { }}
          >
            <FormattedMessage id="MERGE_INDIVIDUALS" />
          </MainButton>
        );

      case "too_many_individuals":
        return (
          <div style={styles.warningText}>
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            <FormattedMessage
              id="TOO_MANY_INDIVIDUALS_WARNING"
            />
          </div>
        );

      default:
        return null;
    }
  };

  return (
    <div style={styles.bottomBar(themeColor)}>
      <div style={styles.bottomText}>
        <FormattedMessage
          id="MATCH_RESULTS_FOR"
        />{" "}
        <span
          style={{
            ...styles.idPill(themeColor),
            ...styles.idPillOutline,
            marginRight: "4px",
          }}
        >
          {store.encounterId}
        </span>
      </div>
      <div
        className="d-flex align-items-center"
        style={{ gap: "12px", marginLeft: "auto" }}
      >
        {renderActions()}
      </div>
    </div>
  );
});

export default MatchResultsBottomBar;