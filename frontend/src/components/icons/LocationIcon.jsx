import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function LocationIcon() {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div
      style={{
        display: "flex",
        width: "36px",
        height: " 36px",
        justifyContent: "center",
        alignItems: "center",
        borderRadius: "50%",
        backgroundColor: theme.primaryColors.primary50,
      }}
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        width="21"
        height="20"
        viewBox="0 0 21 20"
        fill="none"
      >
        <path
          d="M17.4545 2.63647L17.3236 2.66102L12.9545 4.35466L8.04544 2.63647L3.4309 4.19102C3.25908 4.24829 3.13635 4.39557 3.13635 4.58375V16.9547C3.13635 17.1837 3.31635 17.3637 3.54544 17.3637L3.67635 17.3392L8.04544 15.6456L12.9545 17.3637L17.5691 15.8092C17.7409 15.7519 17.8636 15.6047 17.8636 15.4165V3.04557C17.8636 2.81647 17.6836 2.63647 17.4545 2.63647ZM8.86363 4.65738L12.1364 5.80284V15.3428L8.86363 14.1974V4.65738ZM4.77272 5.46738L7.22726 4.64102V14.2137L4.77272 15.1628V5.46738ZM16.2273 14.5328L13.7727 15.3592V5.79466L16.2273 4.84557V14.5328Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
