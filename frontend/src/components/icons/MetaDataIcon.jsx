import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function MetadataIcon() {
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
            }}>
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="14" viewBox="0 0 16 14" fill="none">
                <path d="M1.6591 5.77286C0.980006 5.77286 0.431824 6.32104 0.431824 7.00013C0.431824 7.67922 0.980006 8.22741 1.6591 8.22741C2.33819 8.22741 2.88637 7.67922 2.88637 7.00013C2.88637 6.32104 2.33819 5.77286 1.6591 5.77286ZM1.6591 0.86377C0.980006 0.86377 0.431824 1.41195 0.431824 2.09104C0.431824 2.77013 0.980006 3.31831 1.6591 3.31831C2.33819 3.31831 2.88637 2.77013 2.88637 2.09104C2.88637 1.41195 2.33819 0.86377 1.6591 0.86377ZM1.6591 10.682C0.980006 10.682 0.431824 11.2383 0.431824 11.9092C0.431824 12.5801 0.988187 13.1365 1.6591 13.1365C2.33001 13.1365 2.88637 12.5801 2.88637 11.9092C2.88637 11.2383 2.33819 10.682 1.6591 10.682ZM4.11364 12.7274H15.5682V11.091H4.11364V12.7274ZM4.11364 7.81831H15.5682V6.18195H4.11364V7.81831ZM4.11364 1.27286V2.90922H15.5682V1.27286H4.11364Z" fill="#00ACCE" />
            </svg>
        </div>
    );
}