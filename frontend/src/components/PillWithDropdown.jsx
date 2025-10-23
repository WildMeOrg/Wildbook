import React from "react";
import PropTypes from "prop-types";
import ThemeColorContext from "../ThemeColorProvider";

export default function PillWithDropdown({
  options,
  selectedOption,
  onSelect,
  style,
}) {
  const theme = React.useContext(ThemeColorContext);
  return (
    <div>
      <select
        value={selectedOption}
        onChange={(e) => onSelect(e.target.value)}
        style={{
          display: "inline-block",
          padding: "5px 10px",
          backgroundColor: theme.primaryColors.primary100,
          color: "#000",
          borderRadius: "20px",
          cursor: "pointer",
          width: "130px",
          height: "40px",
          border: "none",
          fontSize: "1rem",
          ...style,
        }}
      >
        {options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </div>
  );
}

PillWithDropdown.propTypes = {
  label: PropTypes.string.isRequired,
  options: PropTypes.arrayOf(
    PropTypes.shape({
      value: PropTypes.string.isRequired,
      label: PropTypes.string.isRequired,
    }),
  ).isRequired,
  selectedOption: PropTypes.string.isRequired,
  onSelect: PropTypes.func.isRequired,
};

PillWithDropdown.defaultProps = {
  selectedOption: "",
};
