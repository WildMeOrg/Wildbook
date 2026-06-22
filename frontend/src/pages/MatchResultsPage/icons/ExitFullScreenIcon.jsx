import React from "react";
import ThemeColorContext from "../../../ThemeColorProvider";

export default function ExitFullScreenIcon({
  onClick = () => {},
  style = {},
  className = "",
}) {
  const themeColor = React.useContext(ThemeColorContext);
  return (
    <div
      style={{
        width: "30px",
        height: "30px",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backgroundColor: themeColor.primaryColors.primary50,
        color: themeColor.primaryColors.primary500,
        borderRadius: "5px",
        padding: "4px",
        boxShadow:
          themeColor === "dark"
            ? "0px 2px 4px rgba(0, 0, 0, 0.2)"
            : "0px 1px 3px rgba(0, 0, 0, 0.1)",
        cursor: "pointer",
        margin: "0 5px",
        ...style,
      }}
      className={className}
      onClick={onClick}
      aria-label="Zoom In"
      role="button"
      tabIndex={0}
      onKeyDown={() => {}}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width="14"
        height="14"
        viewBox="0 0 14 14"
        fill="none"
      >
        <path
          d="M1 11H3V13C3 13.55 3.45 14 4 14C4.55 14 5 13.55 5 13V10C5 9.45 4.55 9 4 9H1C0.45 9 0 9.45 0 10C0 10.55 0.45 11 1 11ZM3 3H1C0.45 3 0 3.45 0 4C0 4.55 0.45 5 1 5H4C4.55 5 5 4.55 5 4V1C5 0.45 4.55 0 4 0C3.45 0 3 0.45 3 1V3ZM10 14C10.55 14 11 13.55 11 13V11H13C13.55 11 14 10.55 14 10C14 9.45 13.55 9 13 9H10C9.45 9 9 9.45 9 10V13C9 13.55 9.45 14 10 14ZM11 3V1C11 0.45 10.55 0 10 0C9.45 0 9 0.45 9 1V4C9 4.55 9.45 5 10 5H13C13.55 5 14 4.55 14 4C14 3.45 13.55 3 13 3H11Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
