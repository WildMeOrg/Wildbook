
import React, { useMemo } from "react";
import Form from "react-bootstrap/Form";
import CreatableSelect from "react-select/creatable";

function normalizeOptions(options = []) {
  return options.map((opt) =>
    typeof opt === "string"
      ? { value: opt, label: opt }
      : { value: String(opt.value), label: opt.label }
  );
}

export default function FreeTextAndSelectInput({
  label = "Select",
  value = "",
  onChange,
  options = [],
  placeholder = "Select or type...",
  size,
  className = "",
  isClearable = true,
  ...props
}) {
  const normalizedOptions = useMemo(() => normalizeOptions(options), [options]);

  const selectedOption = useMemo(() => {
    const found = normalizedOptions.find((o) => o.value === value);
    if (found) return found;
    if (value) return { value, label: value }; 
    return null;
  }, [normalizedOptions, value]);

  const handleChange = (opt) => {
    onChange?.(opt ? opt.value : "");
  };

  const handleCreate = (inputValue) => {
    onChange?.(inputValue); 
  };

  const controlHeights = { sm: 31, md: 38, lg: 49 };
  const minHeight =
    size === "sm" ? controlHeights.sm : size === "lg" ? controlHeights.lg : controlHeights.md;

  return (
    <Form.Group className={className + " mt-2"}>
      {label && <Form.Label>{label}</Form.Label>}
      <CreatableSelect
        value={selectedOption}
        onChange={handleChange}
        onCreateOption={handleCreate}
        options={normalizedOptions}
        placeholder={placeholder}
        isClearable={isClearable}
        menuPortalTarget={typeof document !== "undefined" ? document.body : null}
        styles={{
          menuPortal: (base) => ({ ...base, zIndex: 9999 }),
          control: (base, state) => ({
            ...base,
            minHeight,
            boxShadow: state.isFocused
              ? "0 0 0 0.2rem rgba(13,110,253,.25)"
              : base.boxShadow,
            borderColor: state.isFocused ? "#86b7fe" : base.borderColor,
          }),
        }}
        classNamePrefix="react-select"
        {...props}
      />
    </Form.Group>
  );
}
