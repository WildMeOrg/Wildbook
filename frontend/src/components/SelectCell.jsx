import React from "react";
import Select from "react-select";

const normalize = (str = "") =>
  str.replace(/[^\p{L}\p{N}]/gu, "").toLowerCase();

const filterOption = ({ label }, rawInput) => {
  return normalize(label).includes(normalize(rawInput));
};

const SelectCell = ({ options, value, onChange, onBlur, isInvalid, error }) => {

  return (
    <Select
          options={options}
          value={value}
          onChange={onChange}
          onBlur={onBlur}
          isClearable
          isSearchable
          menuPortalTarget={document.body}
          menuPosition="fixed"
          filterOption={filterOption}
          placeholder="searching"
          className={
            error
              ? "react-select-container is-invalid"
              : "react-select-container"
          }
          classNamePrefix="react-select"
          styles={{
            control: (base) => ({
              ...base,
              borderColor: error ? "red" : base.borderColor,
              boxShadow: error ? "0 0 0 1px red" : base.boxShadow,
            }),
            container: (base) => ({
              ...base,
              minWidth: "150px",
              maxWidth: "250px",
            }),
            menu: (base) => ({
              ...base,
              zIndex: 9999,
            }),
            menuPortal: base => ({ ...base, zIndex: 1060 }),   
          }}
        />      
  );
};

export default SelectCell;
