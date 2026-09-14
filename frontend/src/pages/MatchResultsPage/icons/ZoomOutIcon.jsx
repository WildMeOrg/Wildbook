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
        }} className={className} onClick={onClick} title="Zoom Out" aria-label="Zoom In" role="button" tabIndex={0} onKeyDown={(e) => {
        }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 18 18" fill="none">
                    <path d="M12.5006 11.0006H11.7106L11.4306 10.7306C12.6306 9.33063 13.2506 7.42063 12.9106 5.39063C12.4406 2.61063 10.1206 0.390626 7.32063 0.0506256C3.09063 -0.469374 -0.469374 3.09063 0.0506256 7.32063C0.390626 10.1206 2.61063 12.4406 5.39063 12.9106C7.42063 13.2506 9.33063 12.6306 10.7306 11.4306L11.0006 11.7106V12.5006L15.2606 16.7506C15.6706 17.1606 16.3306 17.1606 16.7406 16.7506L16.7506 16.7406C17.1606 16.3306 17.1606 15.6706 16.7506 15.2606L12.5006 11.0006ZM6.50063 11.0006C4.01063 11.0006 2.00063 8.99063 2.00063 6.50063C2.00063 4.01063 4.01063 2.00063 6.50063 2.00063C8.99063 2.00063 11.0006 4.01063 11.0006 6.50063C11.0006 8.99063 8.99063 11.0006 6.50063 11.0006ZM4.50063 6.00063H8.50063C8.78063 6.00063 9.00063 6.22063 9.00063 6.50063C9.00063 6.78063 8.78063 7.00063 8.50063 7.00063H4.50063C4.22063 7.00063 4.00063 6.78063 4.00063 6.50063C4.00063 6.22063 4.22063 6.00063 4.50063 6.00063Z" fill="#00ACCE" />
                  </svg>
        </div>)
}