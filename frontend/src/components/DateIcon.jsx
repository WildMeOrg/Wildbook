import React from "react";
import ThemeColorContext from "../ThemeColorProvider";

export default function DateIcon() {
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
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 20 20" fill="none">
                <path d="M16.5454 2.63636H15.7273V1.81818C15.7273 1.36818 15.3591 1 14.9091 1C14.4591 1 14.0909 1.36818 14.0909 1.81818V2.63636H5.90909V1.81818C5.90909 1.36818 5.5409 1 5.0909 1C4.6409 1 4.27272 1.36818 4.27272 1.81818V2.63636H3.45454C2.55454 2.63636 1.81818 3.37273 1.81818 4.27273V17.3636C1.81818 18.2636 2.55454 19 3.45454 19H16.5454C17.4454 19 18.1818 18.2636 18.1818 17.3636V4.27273C18.1818 3.37273 17.4454 2.63636 16.5454 2.63636ZM15.7273 17.3636H4.27272C3.82272 17.3636 3.45454 16.9955 3.45454 16.5455V6.72727H16.5454V16.5455C16.5454 16.9955 16.1773 17.3636 15.7273 17.3636Z" fill="#00ACCE" />
            </svg>
        </div>
    );
}