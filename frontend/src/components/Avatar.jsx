import React from "react";

export default function Avatar({ avatar }) {
  return (
    <div
      className="content"
      style={{
        display: "flex",
        justifyContent: "space-around",
        alignItems: "center",
      }}
    >
      <img
        src={avatar}
        alt="img"
        style={{
          width: "35px",
          height: "35px",
          borderRadius: "50%",
        }}
      />
    </div>
  );
}
