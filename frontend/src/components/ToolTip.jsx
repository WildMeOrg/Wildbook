import React from "react";

function Tooltip({ show, x, y, children }) {
  if (!show) return null;
  return (
    <div
      role="tooltip"
      style={{
        position: "absolute",
        left: x + 12,
        top: y + 12,
        background: "rgba(0,0,0,.85)",
        color: "#fff",
        padding: "6px 8px",
        borderRadius: 8,
        pointerEvents: "none",
        maxWidth: 260,
        fontSize: 12,
        lineHeight: 1.4,
        zIndex: 9999,
        whiteSpace: "pre-line", 
      }}
    >
      {children}
    </div>
  );
}

export default Tooltip;