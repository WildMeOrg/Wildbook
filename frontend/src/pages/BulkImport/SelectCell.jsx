import React from "react";
import Select from "react-select";

const SelectCell = ({ options, value, onChange, onBlur, isInvalid }) => {
  const opts = options.map((o) => ({ value: o, label: o }));

  return (
    <Select
      options={opts}
      defaultValue={value ? { value, label: value } : null}
      onChange={(sel) => {
        onChange(sel ? sel.value : "");
      }}
      onBlur={onBlur}
      isClearable
      placeholder="searching…"
      className={
        isInvalid
          ? "react-select-container is-invalid"
          : "react-select-container"
      }
      classNamePrefix="react-select"
    />
  );
};

export default SelectCell;
