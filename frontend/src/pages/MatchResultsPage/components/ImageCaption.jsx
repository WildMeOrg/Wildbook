import React from "react";

const styles = {
  strip: {
    position: "absolute",
    left: 0,
    right: 0,
    bottom: 0,
    zIndex: 5,
    display: "flex",
    flexWrap: "wrap",
    alignItems: "center",
    gap: "6px 14px",
    padding: "8px 12px",
    background: "rgba(17, 17, 17, 0.78)",
    color: "#fff",
    fontSize: "13px",
    lineHeight: 1.3,
  },
  date: { fontWeight: 600 },
  filename: { overflowWrap: "anywhere" },
  location: {
    background: "rgba(255, 255, 255, 0.18)",
    borderRadius: "10px",
    padding: "1px 8px",
  },
};

// Issue #1733: encounter date, image filename and (when the server sent it,
// i.e. the viewer may see that encounter) location ID along the bottom of a
// match image. Renders nothing when all three are missing.
const ImageCaption = ({ date, filename, locationId, testId }) => {
  if (!date && !filename && !locationId) return null;
  return (
    <div style={styles.strip} data-testid={testId}>
      {date && (
        <span style={styles.date} data-testid={`${testId}-date`}>
          {date}
        </span>
      )}
      {filename && (
        <span style={styles.filename} data-testid={`${testId}-filename`}>
          {filename}
        </span>
      )}
      {locationId && (
        <span style={styles.location} data-testid={`${testId}-location`}>
          {locationId}
        </span>
      )}
    </div>
  );
};

export default ImageCaption;
