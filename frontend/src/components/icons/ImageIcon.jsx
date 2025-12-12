import React from "react";
import ThemeColorContext from "../../ThemeColorProvider";

export default function ImageIcon() {
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
            <svg xmlns="http://www.w3.org/2000/svg" width="21" height="21" viewBox="0 0 21 21" fill="none">
                <path d="M10.7011 13.8405C12.0968 13.8405 13.2282 12.7091 13.2282 11.3134C13.2282 9.91769 12.0968 8.78626 10.7011 8.78626C9.30538 8.78626 8.17395 9.91769 8.17395 11.3134C8.17395 12.7091 9.30538 13.8405 10.7011 13.8405Z" fill="#00ACCE" />
                <path d="M17.4401 4.57439H14.7697L13.7252 3.43719C13.4135 3.09182 12.9586 2.88965 12.4869 2.88965H8.91524C8.44351 2.88965 7.98863 3.09182 7.66853 3.43719L6.63241 4.57439H3.96209C3.03548 4.57439 2.27734 5.33253 2.27734 6.25914V16.3676C2.27734 17.2942 3.03548 18.0524 3.96209 18.0524H17.4401C18.3667 18.0524 19.1248 17.2942 19.1248 16.3676V6.25914C19.1248 5.33253 18.3667 4.57439 17.4401 4.57439ZM10.7011 15.5252C8.37612 15.5252 6.48921 13.6383 6.48921 11.3134C6.48921 8.98843 8.37612 7.10151 10.7011 7.10151C13.026 7.10151 14.9129 8.98843 14.9129 11.3134C14.9129 13.6383 13.026 15.5252 10.7011 15.5252Z" fill="#00ACCE" />
            </svg>
        </div>
    );
}