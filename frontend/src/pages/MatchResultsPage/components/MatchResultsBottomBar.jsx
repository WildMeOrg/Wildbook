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
              noArrow
              backgroundColor={themeColor.primaryColors.primary500}
              color="white"
              onClick={store.handleConfirmNoMatch}
              disabled={!String(store.newIndividualName || "").trim() || store.matchRequestLoading}
              style={{ marginTop: "0", marginBottom: "0" }}
            >
              <FormattedMessage id="CONFIRM_NO_MATCH" />
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

      case "single_individual":
        return (
          <MainButton
            noArrow
            backgroundColor={themeColor.primaryColors.primary500}
            color="white"
            onClick={store.handleMatch}
            disabled={store.matchRequestLoading}
            style={{ marginTop: "0", marginBottom: "0" }}
          >
            <FormattedMessage id="CONFIRM_MATCH" defaultMessage="Confirm Match" />
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
        );

      case "two_individuals":
        return (
          <MainButton
            color="white"
            backgroundColor={themeColor.primaryColors.primary700}
            noArrow
            onClick={store.handleMerge}
            disabled={store.matchRequestLoading}
            style={{ marginTop: "0", marginBottom: "0" }}
          >
            <FormattedMessage id="MERGE_INDIVIDUALS" defaultMessage="Merge Individuals" />
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
        );

      case "too_many_individuals":
        return (
          <div style={styles.warningText}>
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            <FormattedMessage
              id="CANNOT_MERGE_MORE_THAN_TWO"
              defaultMessage="Can't merge more than 2 individuals."
            />
          </div>
        );

      case "no_further_action_needed":
        return (
          <div style={styles.bottomText}>
            <FormattedMessage
              id="NO_FURTHER_ACTION_NEEDED"
              defaultMessage="No further action needed."
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