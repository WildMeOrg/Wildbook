// PillWithButton.jsx
import React from "react";
import ThemeColorContext from "../ThemeColorProvider";

export default function PillWithButton({ text, onClick, onClose }) {
  const theme = React.useContext(ThemeColorContext);

  return (
    <div
      className="d-flex align-items-center me-3 mb-2"
      style={{
        display: "inline-flex",
        alignItems: "center",
        padding: "5px 10px",
        backgroundColor: theme.primaryColors.primary100,
        color: "#000",
        borderRadius: "20px",
        cursor: onClick ? "pointer" : "default",
        boxShadow:
          "inset 0 1px 0 rgba(255,255,255,.5), 0 1px 2px rgba(0,0,0,.08)",
      }}
    >
      <span>{text}</span>
      <button
        type="button"
        className="btn-close ms-2"
        aria-label="Close"
        onClick={(e) => {
          e.stopPropagation();
          onClose?.();
        }}
        style={{
          width: 4,
          height: 4,
          filter: "drop-shadow(0 1px 1px rgba(0,0,0,.25))",
        }}
      />
    </div>
  );
}
