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
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">
                    <path d="M19.1039 10.39L20.1639 9.33C20.9439 8.55 20.9439 7.28 20.1639 6.5L18.7539 5.09C17.9739 4.31 16.7039 4.31 15.9239 5.09L14.8639 6.15L19.1039 10.39ZM14.8639 11.81L7.66391 19H6.25391V17.59L13.4439 10.4L14.8639 11.81ZM13.4439 7.56L4.25391 16.76V21H8.49391L17.6839 11.81L13.4439 7.56ZM19.2539 17.5C19.2539 19.69 16.7139 21 14.2539 21C13.7039 21 13.2539 20.55 13.2539 20C13.2539 19.45 13.7039 19 14.2539 19C15.7939 19 17.2539 18.27 17.2539 17.5C17.2539 17.03 16.7739 16.63 16.0239 16.3L17.5039 14.82C18.5739 15.45 19.2539 16.29 19.2539 17.5ZM4.83391 13.35C3.86391 12.79 3.25391 12.06 3.25391 11C3.25391 9.2 5.14391 8.37 6.81391 7.64C7.84391 7.18 9.25391 6.56 9.25391 6C9.25391 5.59 8.47391 5 7.25391 5C5.99391 5 5.45391 5.61 5.42391 5.64C5.07391 6.05 4.44391 6.1 4.02391 5.76C3.61391 5.42 3.53391 4.81 3.87391 4.38C3.98391 4.24 5.01391 3 7.25391 3C9.49391 3 11.2539 4.32 11.2539 6C11.2539 7.87 9.32391 8.72 7.61391 9.47C6.67391 9.88 5.25391 10.5 5.25391 11C5.25391 11.31 5.68391 11.6 6.32391 11.86L4.83391 13.35Z" fill="#00ACCE" />
                  </svg>
        </div>)
}