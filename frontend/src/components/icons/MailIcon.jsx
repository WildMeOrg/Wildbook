import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function MailIcon() {
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
        width="20"
        height="20"
        viewBox="0 0 20 20"
        fill="none"
      >
        <path
          d="M16.5451 3.45459H3.45423C2.55423 3.45459 1.82605 4.19095 1.82605 5.09095L1.81787 14.9091C1.81787 15.8091 2.55423 16.5455 3.45423 16.5455H16.5451C17.4451 16.5455 18.1815 15.8091 18.1815 14.9091V5.09095C18.1815 4.19095 17.4451 3.45459 16.5451 3.45459ZM16.2179 6.93186L10.4333 10.5482C10.1715 10.7119 9.82787 10.7119 9.56605 10.5482L3.78151 6.93186C3.57696 6.80095 3.45423 6.58004 3.45423 6.34277C3.45423 5.79459 4.05151 5.46732 4.51787 5.75368L9.99969 9.18186L15.4815 5.75368C15.9479 5.46732 16.5451 5.79459 16.5451 6.34277C16.5451 6.58004 16.4224 6.80095 16.2179 6.93186Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
