import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function PlusIcon() {
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
            <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 18 18" fill="none">
                <path d="M15.9997 10.1663H10.1663V15.9997C10.1663 16.6413 9.64134 17.1663 8.99967 17.1663C8.35801 17.1663 7.83301 16.6413 7.83301 15.9997V10.1663H1.99967C1.35801 10.1663 0.833008 9.64134 0.833008 8.99967C0.833008 8.35801 1.35801 7.83301 1.99967 7.83301H7.83301V1.99967C7.83301 1.35801 8.35801 0.833008 8.99967 0.833008C9.64134 0.833008 10.1663 1.35801 10.1663 1.99967V7.83301H15.9997C16.6413 7.83301 17.1663 8.35801 17.1663 8.99967C17.1663 9.64134 16.6413 10.1663 15.9997 10.1663Z" fill="#00A5CE" />
            </svg>
        </div>
    );
}