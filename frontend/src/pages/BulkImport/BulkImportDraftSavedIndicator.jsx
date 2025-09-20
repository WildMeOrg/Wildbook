import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";

const DraftSaveIndicator = observer(({ store }) => {
  if (store.isSavingDraft) {
    return (
      <Box>
        <FormattedMessage id="BULK_IMPORT_SAVING_AS_DRAFT" />
      </Box>
    );
  }

  if (store.lastSavedAt) {
    return (
      <Box>
        <i
          className="bi bi-check-circle-fill me-2"
          style={{ fontSize: 20, color: "#20c997" }}
        />
        <FormattedMessage id="BULK_IMPORT_SAVED_AS_DRAFT" />
      </Box>
    );
  }

  return null;
});

export default DraftSaveIndicator;

const Box = ({ children }) => (
  <div
    style={{
      height: "24px",
      display: "flex",
      alignItems: "center",
    }}
    id="draft-save-indicator"
  >
    {children}
  </div>
);
