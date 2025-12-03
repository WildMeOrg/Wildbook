import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Form, Button } from "react-bootstrap";
import MainButton from "../../components/MainButton";

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
              disabled={!store.newIndividualName.trim()}
              style={{ marginTop: "0", marginBottom: "0" }}
            >
              <FormattedMessage
                id="CONFIRM_NO_MATCH"
                defaultMessage="Confirm No Match"
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
              onClick={store.handleConfirmNoMatch}
              disabled={!store.newIndividualName.trim()}
              style={{ marginTop: "0", marginBottom: "0" }}
            >
              <FormattedMessage
                id="CONFIRM_NO_MATCH"
                defaultMessage="Confirm No Match"
              />
            </MainButton>
          </>
        );

      case "single_individual":
        return (
          <Button variant="primary" size="sm" onClick={store.handleConfirmMatch}>
            <FormattedMessage
              id="CONFIRM_MATCH"
              defaultMessage="Confirm Match"
            />
          </Button>
        );

      case "two_individuals":
        return (
          <Button variant="success" size="sm" onClick={store.handleMerge}>
            <FormattedMessage
              id="MERGE_INDIVIDUALS"
              defaultMessage="Merge Individuals"
            />
          </Button>
        );

      case "too_many_individuals":
        return (
          <div style={styles.warningText}>
            <i className="bi bi-exclamation-triangle-fill me-2"></i>
            <FormattedMessage
              id="TOO_MANY_INDIVIDUALS_WARNING"
              defaultMessage="You cannot merge more than two individuals"
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
          defaultMessage="Match results for"
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