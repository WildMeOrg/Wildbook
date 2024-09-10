import React from "react";
import logoPath from "./logo.svg"

export default function Logo({ style }) {
  return (
    <div className="navbar-brand"
      style={{
        height: "50px",
        width: "100px",
        overflow: "hidden",
        display: "inline-block",
        backgroundImage: `url(${logoPath})`,
        backgroundSize: "contain",
        backgroundRepeat: "no-repeat",
        backgroundPosition: "right center",
        ...style,
      }}
    >
    </div>
  );
}
