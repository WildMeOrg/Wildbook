import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function AttributesIcon() {
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
        width="17"
        height="17"
        viewBox="0 0 17 17"
        fill="none"
      >
        <path
          d="M16.199 7.90654L8.83539 0.542905C8.54084 0.248359 8.13175 0.0683594 7.68175 0.0683594H1.95448C1.05448 0.0683594 0.318115 0.804723 0.318115 1.70472V7.432C0.318115 7.882 0.498115 8.29109 0.800842 8.59381L8.16448 15.9575C8.45902 16.252 8.86812 16.432 9.31812 16.432C9.76812 16.432 10.1772 16.252 10.4718 15.9493L16.199 10.222C16.5018 9.92745 16.6818 9.51836 16.6818 9.06836C16.6818 8.61836 16.4936 8.20109 16.199 7.90654ZM3.18175 4.15927C2.50266 4.15927 1.95448 3.61109 1.95448 2.932C1.95448 2.2529 2.50266 1.70472 3.18175 1.70472C3.86084 1.70472 4.40902 2.2529 4.40902 2.932C4.40902 3.61109 3.86084 4.15927 3.18175 4.15927Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
