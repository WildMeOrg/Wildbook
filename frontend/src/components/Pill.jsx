import React from "react";
import ThemeColorContext from "../ThemeColorProvider";

export default function Pill({ text, onClick, style, active }) {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div
      onClick={onClick}
      style={{
        display: "inline-block",
        padding: "5px 10px",
        backgroundColor: active
          ? theme.primaryColors.primary500
          : theme.primaryColors.primary100,
        color: active ? "#fff" : "black",
        borderRadius: "20px",
        cursor: "pointer",
        ...style,
      }}
    >
      {text}
    </div>
  );
}
