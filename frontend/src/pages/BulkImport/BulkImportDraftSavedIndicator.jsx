import React from "react";
import { observer } from "mobx-react-lite";

const DraftSaveIndicator = observer(({ store }) => {
  if (store.isSavingDraft) {
    return (
      <Box>Saving&nbsp;as&nbsp;draft…</Box>
    );
  }

  if (store.lastSavedAt) {
    return (
      <Box><i className="bi bi-check-circle-fill me-2" style={{ fontSize: 20, color: '#20c997' }} />Saved&nbsp;as&nbsp;draft</Box>
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
  >
    {children}
  </div>
);
