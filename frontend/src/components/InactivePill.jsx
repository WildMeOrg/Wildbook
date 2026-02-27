

import React from "react";
import PropTypes from "prop-types";
import ThemeColorContext from "../ThemeColorProvider";

export default function InactivePill({ text, onClick, style }) {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div
      onClick={onClick}
      style={{
        display: "inline-block",
        padding: "5px 10px",
        backgroundColor: theme.primaryColors.primary100,
        color: "#000",
        borderRadius: "20px",
        cursor: "pointer",
        ...style, 
      }}
    >
      {text}
    </div>
  );
}
InactivePill.propTypes = {
  text: PropTypes.string.isRequired,
  onClick: PropTypes.func.isRequired,
};