import React from "react";
import Form from "react-bootstrap/Form";

export default function SelectInput({
  label = "Select",
  value = "",
  onChange,
  options = [],
  placeholder = "Select an option",
  size,
  className = "",
  ...props
}) {
  return (
    <Form.Group className={className}>
      {label && <Form.Label>{label}</Form.Label>}
      <Form.Select
        value={value}
        onChange={(e) => onChange?.(e.target.value)}
        size={size}
        {...props}
      >
        <option value="" disabled>
          {placeholder}
        </option>
        {options.map((opt) => {
          if (typeof opt === "string") {
            return (
              <option key={opt} value={opt}>
                {opt}
              </option>
            );
          }
          return (
            <option
              key={String(opt.value)}
              value={opt.value}
              disabled={opt.disabled}
            >
              {opt.label || opt.title || opt.value}
            </option>
          );
        })}
      </Form.Select>
    </Form.Group>
  );
}
