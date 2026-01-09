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
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 18 18" fill="none">
                    <g clip-path="url(#clip0_252_12216)">
                      <path d="M15.8844 3C15.2619 3 14.7594 3.5025 14.7594 4.125V7.875C14.7594 8.085 14.5944 8.25 14.3844 8.25C14.1744 8.25 14.0094 8.085 14.0094 7.875V1.875C14.0094 1.2525 13.5069 0.75 12.8844 0.75C12.2619 0.75 11.7594 1.2525 11.7594 1.875V7.875C11.7594 8.085 11.5944 8.25 11.3844 8.25C11.1744 8.25 11.0094 8.085 11.0094 7.875V1.125C11.0094 0.5025 10.5069 0 9.88437 0C9.26187 0 8.75937 0.5025 8.75937 1.125V7.8675C8.75937 8.0775 8.59437 8.2425 8.38437 8.2425C8.17437 8.2425 8.00937 8.0775 8.00937 7.8675V3.375C8.00937 2.7525 7.50687 2.25 6.88437 2.25C6.26187 2.25 5.75937 2.7525 5.75937 3.375V11.9325L2.66937 10.17C2.23437 9.9225 1.69437 9.99 1.33437 10.335C0.884366 10.77 0.869366 11.49 1.31187 11.9325L6.39687 17.1C6.95937 17.6775 7.72437 18 8.53437 18H14.0094C15.6669 18 17.0094 16.6575 17.0094 15V4.125C17.0094 3.5025 16.5069 3 15.8844 3Z" fill="#00ACCE" />
                    </g>
                    <defs>
                      <clipPath id="clip0_252_12216">
                        <rect width="18" height="18" fill="white" />
                      </clipPath>
                    </defs>
                  </svg>
        </div>)
}