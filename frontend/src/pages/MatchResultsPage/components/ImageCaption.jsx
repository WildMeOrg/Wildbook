import React from "react";

const field = {
  minWidth: 0,
  maxWidth: "100%",
  overflow: "hidden",
  textOverflow: "ellipsis",
  whiteSpace: "nowrap",
  // the strip lets drags and wheel zoom through to the image; the values
  // themselves stay hoverable so a truncated one shows in full as a tooltip
  pointerEvents: "auto",
};

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
    gap: "4px 8px",
    padding: "8px 12px",
    background: "rgba(17, 17, 17, 0.78)",
    color: "#fff",
    fontSize: "13px",
    lineHeight: 1.3,
    pointerEvents: "none",
  },
  date: { ...field, fontWeight: 600 },
  filename: { ...field, flex: "0 1 auto" },
  location: {
    ...field,
    background: "rgba(255, 255, 255, 0.18)",
    borderRadius: "10px",
    padding: "1px 8px",
  },
  separator: { opacity: 0.6 },
};

// Issue #1733: encounter date, image filename and (when the server sent it,
// i.e. the viewer may see that encounter) location ID along the bottom of a
// match image. Each value stays on one line, truncated with its full text as
// a tooltip. Renders nothing when all three are missing.
const ImageCaption = ({ date, filename, locationId, testId }) => {
  const parts = [
    date && { key: "date", style: styles.date, value: date },
    filename && { key: "filename", style: styles.filename, value: filename },
    locationId && {
      key: "location",
      style: styles.location,
      value: locationId,
    },
  ].filter(Boolean);
  if (parts.length === 0) return null;
  return (
    <div style={styles.strip} data-testid={testId}>
      {parts.map((part, i) => (
        <React.Fragment key={part.key}>
          {i > 0 && (
            <span style={styles.separator} aria-hidden="true">
              {" · "}
            </span>
          )}
          <span
            style={part.style}
            title={part.value}
            data-testid={`${testId}-${part.key}`}
          >
            {part.value}
          </span>
        </React.Fragment>
      ))}
    </div>
  );
};

export default ImageCaption;
