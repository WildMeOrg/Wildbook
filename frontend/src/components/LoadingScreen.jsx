import React, { useState, useEffect } from "react";

export default function LoadingScreen() {
  const [showSpinner, setShowSpinner] = useState(false);

  useEffect(() => {
    const timeout = setTimeout(() => setShowSpinner(true), 1000);
    return () => clearTimeout(timeout);
  }, []);

  return (
    <div
      className={`${showSpinner ? "d-flex" : "d-none"} justify-content-center align-items-center`}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        zIndex: 1050,
      }}
    >
      <div
        className="spinner-border text-primary"
        style={{ width: "3rem", height: "3rem" }}
        role="status"
      >
        <span className="visually-hidden">Loading...</span>
      </div>
    </div>
  );
}
