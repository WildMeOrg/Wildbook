import React from "react";

export default function FooterLink({ href, text }) {
  return (
    <a
      href={href}
      style={{
        color: "black",
        textDecoration: "none",
        fontSize: "1rem",
        marginBottom: "5px",
        display: "block",
      }}
    >
      {text}
    </a>
  );
}
