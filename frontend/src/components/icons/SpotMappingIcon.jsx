import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function SpotMappingIcon({ className }) {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div
      className={className}
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
        width="24"
        height="24"
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path
          d="M7.2002 17.2C8.85705 17.2 10.2002 15.8569 10.2002 14.2C10.2002 12.5432 8.85705 11.2 7.2002 11.2C5.54334 11.2 4.2002 12.5432 4.2002 14.2C4.2002 15.8569 5.54334 17.2 7.2002 17.2Z"
          fill="#00ACCE"
        />
        <path
          d="M11.2002 9.20001C12.857 9.20001 14.2002 7.85687 14.2002 6.20001C14.2002 4.54316 12.857 3.20001 11.2002 3.20001C9.54334 3.20001 8.2002 4.54316 8.2002 6.20001C8.2002 7.85687 9.54334 9.20001 11.2002 9.20001Z"
          fill="#00ACCE"
        />
        <path
          d="M16.8002 20.8C18.4571 20.8 19.8002 19.4569 19.8002 17.8C19.8002 16.1432 18.4571 14.8 16.8002 14.8C15.1433 14.8 13.8002 16.1432 13.8002 17.8C13.8002 19.4569 15.1433 20.8 16.8002 20.8Z"
          fill="#00ACCE"
        />
      </svg>
    </div>
  );
}
