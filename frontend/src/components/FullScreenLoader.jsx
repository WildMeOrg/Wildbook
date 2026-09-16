import React from "react";

export default function LocalLoader() {
  return (
    <div
      role="status"
      aria-live="polite"
      aria-busy="true"
      style={{
        position: "absolute",
        left: 0,
        top: 0,
        width: "100%",
        height: "100%",
        minHeight: 500,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 10,
        background: "#6c757d",
        borderRadius: 8,
        marginTop: 5,
      }}
    >
      <div
        className="spinner-border text-light"
        role="presentation"
        aria-hidden="true"
      />
      <span className="visually-hidden">Loading…</span>
    </div>
  );
}
