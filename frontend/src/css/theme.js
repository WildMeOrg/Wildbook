// import {
//     darken,
//     lighten,
//   } from '@material-ui/core/styles/colorManipulator';
  
  export const lato = [
    'Lato',
    '-apple-system',
    'BlinkMacSystemFont',
    '"Segoe UI"',
    'Roboto',
    '"Helvetica Neue"',
    'Arial',
    'sans-serif',
    '"Apple Color Emoji"',
    '"Segoe UI Emoji"',
    '"Segoe UI Symbol"',
  ].join(',');
  
  const blackColor = '#2B2351';
  const whiteColor = '#ffffff';
  
  export default primaryColor => {
    // const lightPrimaryColor = lighten(primaryColor, 0.7);
    // const darkPrimaryColor = darken(primaryColor, 0.2);
  
    return {
      palette: {
        common: { black: blackColor },
        primary: { main: primaryColor },
        secondary: { main: darkPrimaryColor },
        paper: { main: '#eeeeee' },
        text: { primary: blackColor, secondary: '#6D6B7B', tertiary: '#9D9CAC' },
      },
      typography: {
        fontFamily: lato,
        h1: { fontWeight: 800, letterSpacing: '0.02em' },
        h2: { fontWeight: 800, letterSpacing: '0.02em' },
        h5: { fontWeight: 700, letterSpacing: '0.04em' },
        h6: {
          fontSize: 14,
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '0.02em',
        },
        body1: { fontWeight: 400 },
        body2: { fontWeight: 400, fontSize: 14 },
        caption: {
          fontWeight: 300,
          fontSize: 14,
          letterSpacing: '0.02em',
          color: darken(blackColor, 0.5),
          lineHeight: 1.2,
        },
        subtitle1: { fontSize: 20, letterSpacing: '0.02em' },
      },
      overrides: {
        MuiTypography: { colorTextSecondary: { color: '#0E1014' } },
        MuiFormHelperText: { root: { color: '#0E1014' } },
        MuiAlert: {
          action: {
            alignItems: 'start',
          },
        },
        MuiButton: {
          label: {
            letterSpacing: '0.02em',
            fontSize: 16,
            fontWeight: 500,
          },
          root: {
            // borderRadius: 10000,
            padding: '6px 20px',
            fontWeight: 600,
          },
          outlined: { padding: '6px 20px' },
          containedSizeSmall: { padding: '4px 16px' },
          outlinedSizeSmall: { padding: '4px 12px' },
          containedSecondary: { backgroundColor: lightPrimaryColor },
          contained: {
            backgroundColor: whiteColor,
            boxShadow: '1px 2px 6px -2px rgba(0,0,0,0.2)',
          },
        },
        MuiTableCell: { root: { fontStyle: 'unset' } },
        MuiListItem: {
          root: {
            '&$selected': { backgroundColor: lightPrimaryColor },
          },
        },
        MuiTreeItem: {
          root: {
            '&$selected': {
              '&& > .MuiTreeItem-content .MuiTreeItem-label': {
                backgroundColor: lightPrimaryColor,
              },
            },
          },
        },
      },
    };
  };
  