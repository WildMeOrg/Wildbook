import React from "react";
import PropTypes from "prop-types";
import { FormattedMessage } from "react-intl";

export default function TextInput({
  value,
  onChange,
  placeholder = "Enter text",
  className = "",
  label = "Text Input",
  ...props
}) {
  return (
    <div className={`text-input-container ${className}`}>
      {label && (
        <h6 className="mt-2 mb-2">{<FormattedMessage id={label} />}</h6>
      )}
      <input
        type="text"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={`form-control ${className}`}
        {...props}
      />
    </div>
  );
}

TextInput.propTypes = {
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
  placeholder: PropTypes.string,
  className: PropTypes.string,
};
