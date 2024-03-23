import React from 'react';

const ThemeContext = React.createContext(
    {
        primaryColors: {
            primary50: '#E5F6FF',
            primary100: '#CCF0FF',
            primary400: '#33D6FF',
            primary500: '#00ACCE',
            primary600: '#00A5CE',
            primary800: '#004B66',
        }
    }
);



export default ThemeContext;