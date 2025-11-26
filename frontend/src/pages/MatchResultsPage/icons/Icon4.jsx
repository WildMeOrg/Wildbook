import React from "react";
import ThemeColorContext from "../../../ThemeColorProvider";

export default function ZoomInIcon({
    onClick = () => {},
    style = {},
    className = ""
}) {
    const themeColor = React.useContext(ThemeColorContext);
    return (
        <div style={{
            width: "30px",
            height: "30px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            backgroundColor: themeColor.primaryColors.primary50,
            color: themeColor.primaryColors.primary500,
            borderRadius: "5px",
            padding: "4px",
            boxShadow: themeColor === "dark" ? "0px 2px 4px rgba(0, 0, 0, 0.2)" : "0px 1px 3px rgba(0, 0, 0, 0.1)",
            cursor: "pointer",
            margin: "0 5px",
            ...style
        }} className={className} onClick={onClick} aria-label="Zoom In" role="button" tabIndex={0} onKeyDown={(e) => {
        }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20" fill="none">
                    <path d="M16.2583 2.56667L2.56667 16.2583C2.64167 16.5417 2.79167 16.8 2.99167 17.0083C3.2 17.2083 3.45833 17.3583 3.74167 17.4333L17.4417 3.74167C17.2833 3.16667 16.8333 2.71667 16.2583 2.56667ZM9.9 2.5L2.5 9.9V12.2583L12.2583 2.5H9.9ZM4.16667 2.5C3.25 2.5 2.5 3.25 2.5 4.16667V5.83333L5.83333 2.5H4.16667ZM15.8333 17.5C16.2917 17.5 16.7083 17.3167 17.0083 17.0083C17.3167 16.7083 17.5 16.2917 17.5 15.8333V14.1667L14.1667 17.5H15.8333ZM7.74167 17.5H10.1L17.5 10.1V7.74167L7.74167 17.5Z" fill="#00ACCE" />
                  </svg>
        </div>)
}