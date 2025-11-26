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
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16" fill="none">
                    <path d="M6 8C4.9 8 4 8.9 4 10C4 11.1 4.9 12 6 12C7.1 12 8 11.1 8 10C8 8.9 7.1 8 6 8ZM2 4C0.9 4 0 4.9 0 6C0 7.1 0.9 8 2 8C3.1 8 4 7.1 4 6C4 4.9 3.1 4 2 4ZM2 12C0.9 12 0 12.9 0 14C0 15.1 0.9 16 2 16C3.1 16 4 15.1 4 14C4 12.9 3.1 12 2 12ZM14 4C15.1 4 16 3.1 16 2C16 0.9 15.1 0 14 0C12.9 0 12 0.9 12 2C12 3.1 12.9 4 14 4ZM10 12C8.9 12 8 12.9 8 14C8 15.1 8.9 16 10 16C11.1 16 12 15.1 12 14C12 12.9 11.1 12 10 12ZM14 8C12.9 8 12 8.9 12 10C12 11.1 12.9 12 14 12C15.1 12 16 11.1 16 10C16 8.9 15.1 8 14 8ZM10 4C8.9 4 8 4.9 8 6C8 7.1 8.9 8 10 8C11.1 8 12 7.1 12 6C12 4.9 11.1 4 10 4ZM6 0C4.9 0 4 0.9 4 2C4 3.1 4.9 4 6 4C7.1 4 8 3.1 8 2C8 0.9 7.1 0 6 0Z" fill="#00ACCE" />
                  </svg>
        </div>)
}