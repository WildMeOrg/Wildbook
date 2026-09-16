import React from "react";
import { Form } from "react-bootstrap";

const NumberInput = ({
  value,
  onChange,
  placeholder = "input number",
  label,
  ...props
}) => {
  const handleChange = (e) => {
    const inputValue = e.target.value;

    if (inputValue === "") {
      onChange("");
      return;
    }

    if (/^-?\d*\.?\d*$/.test(inputValue)) {
      onChange(inputValue);
    }
  };

  return (
    <Form.Group>
      {label && <Form.Label>{label}</Form.Label>}
      <Form.Control
        type="text"
        value={value}
        onChange={handleChange}
        placeholder={placeholder}
        {...props}
      />
    </Form.Group>
  );
};

export default NumberInput;
