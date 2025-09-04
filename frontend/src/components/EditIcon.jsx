import React from "react";
import ThemeColorContext from "../ThemeColorProvider";

export default function EditIcon() {
    const theme = React.useContext(ThemeColorContext);
    return (
        <div
           >
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none">
                <path d="M2.99878 17.4613V20.5013C2.99878 20.7813 3.21878 21.0013 3.49878 21.0013H6.53878C6.66878 21.0013 6.79878 20.9513 6.88878 20.8513L17.8088 9.94128L14.0588 6.19128L3.14878 17.1013C3.04878 17.2013 2.99878 17.3213 2.99878 17.4613ZM20.7088 7.04128C21.0988 6.65128 21.0988 6.02128 20.7088 5.63128L18.3688 3.29128C17.9788 2.90128 17.3488 2.90128 16.9588 3.29128L15.1288 5.12128L18.8788 8.87128L20.7088 7.04128Z" fill="#00A5CE" />
            </svg>
        </div>
    );
}