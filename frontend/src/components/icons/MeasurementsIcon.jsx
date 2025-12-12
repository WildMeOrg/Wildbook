import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function MeasurementsIcon() {
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
          d="M11.5 6.33C12.35 6.03 13.03 5.35 13.33 4.5H16.5L13.5 11.5C13.5 13.16 15.07 14.5 17 14.5C18.93 14.5 20.5 13.16 20.5 11.5L17.5 4.5H19.5V2.5H13.33C12.92 1.33 11.81 0.5 10.5 0.5C9.19 0.5 8.08 1.33 7.67 2.5H1.5V4.5H3.5L0.5 11.5C0.5 13.16 2.07 14.5 4 14.5C5.93 14.5 7.5 13.16 7.5 11.5L4.5 4.5H7.67C7.97 5.35 8.65 6.03 9.5 6.33V17.5H0.5V19.5H20.5V17.5H11.5V6.33ZM18.87 11.5H15.13L17 7.14L18.87 11.5ZM5.87 11.5H2.13L4 7.14L5.87 11.5ZM10.5 4.5C9.95 4.5 9.5 4.05 9.5 3.5C9.5 2.95 9.95 2.5 10.5 2.5C11.05 2.5 11.5 2.95 11.5 3.5C11.5 4.05 11.05 4.5 10.5 4.5Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
