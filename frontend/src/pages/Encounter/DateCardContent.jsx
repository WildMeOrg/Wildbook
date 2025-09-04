
import React from "react";
import PropTypes from "prop-types";
import TextInput from "../../components/TextInput";

export default function DateCardContent({ value, onChange }) {
  return (
    <div className="date-card-content">
      <h6>Encounter Date</h6>
      <TextInput
        value={value}
        onChange={onChange}
        placeholder="Enter date"
        title="Date Input"
      />
    </div>
  );
}

DateCardContent.propTypes = {
  value: PropTypes.string.isRequired,
  onChange: PropTypes.func.isRequired,
};