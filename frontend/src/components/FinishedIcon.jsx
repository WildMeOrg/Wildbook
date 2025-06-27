
import React from 'react';
import { FaCheck } from 'react-icons/fa';
import ThemeContext from '../ThemeColorProvider';

export const FinishedIcon = () => {
    const theme = React.useContext(ThemeContext);
    return (
        <div
            style={{
                width: 40,
                height: 40,
                borderRadius: "50%",
                backgroundColor: theme.primaryColors.primary50,
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
            }}
        >
            <FaCheck size={20} color={theme.primaryColors.primary500} />
        </div>
    );
}