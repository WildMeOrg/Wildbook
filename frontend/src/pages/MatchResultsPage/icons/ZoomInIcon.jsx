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
        }} className={className} onClick={onClick} title="Zoom In" aria-label="Zoom In" role="button" tabIndex={0} onKeyDown={(e) => {
        }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M12.4989 11.0006H11.7089L11.4289 10.7306C12.6289 9.33063 13.2489 7.42063 12.9089 5.39063C12.4389 2.61063 10.1189 0.390626 7.31892 0.0506256C3.08892 -0.469374 -0.46108 3.09063 0.0489202 7.32063C0.38892 10.1206 2.60892 12.4406 5.38892 12.9106C7.41892 13.2506 9.32892 12.6306 10.7289 11.4306L10.9989 11.7106V12.5006L15.2589 16.7506C15.6689 17.1606 16.3289 17.1606 16.7389 16.7506L16.7489 16.7406C17.1589 16.3306 17.1589 15.6706 16.7489 15.2606L12.4989 11.0006ZM6.49892 11.0006C4.00892 11.0006 1.99892 8.99063 1.99892 6.50063C1.99892 4.01063 4.00892 2.00063 6.49892 2.00063C8.98892 2.00063 10.9989 4.01063 10.9989 6.50063C10.9989 8.99063 8.98892 11.0006 6.49892 11.0006ZM6.49892 4.00063C6.21892 4.00063 5.99892 4.22063 5.99892 4.50063V6.00063H4.49892C4.21892 6.00063 3.99892 6.22063 3.99892 6.50063C3.99892 6.78063 4.21892 7.00063 4.49892 7.00063H5.99892V8.50063C5.99892 8.78063 6.21892 9.00063 6.49892 9.00063C6.77892 9.00063 6.99892 8.78063 6.99892 8.50063V7.00063H8.49892C8.77892 7.00063 8.99892 6.78063 8.99892 6.50063C8.99892 6.22063 8.77892 6.00063 8.49892 6.00063H6.99892V4.50063C6.99892 4.22063 6.77892 4.00063 6.49892 4.00063Z" fill="#00ACCE" />
            </svg>
        </div>)
}