import React from "react";
import Select from "react-select";
import { useEffect, useState } from "react";

const colourStyles = {
  option: (styles) => ({
    ...styles,
    color: "black",
  }),
  singleValue: (styles) => ({ ...styles, color: "black" }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
  // menu: base => ({ ...base, maxHeight: '200px' }),
  control: (base) => ({ ...base, zIndex: 1, backgroundColor: "white" }),
};

export default function OrSelecter({
  isMulti,
  options,
  onChange,
  field,
  filterKey,
  term,
}) {
  const [selectedOptions, setSelectedOptions] = useState([]);

  useEffect(() => {
    onChange(null, field);
    return () => {
      options.forEach((option) => {
        console.log(option);
        onChange(null, `${field}.${option.value}`);
      });
    };
  }, []);

  return (
    <Select
      isMulti={isMulti}
      options={options}
      className="basic-multi-select"
      classNamePrefix="select"
      styles={colourStyles}
      menuPlacement="auto"
      menuPortalTarget={document.body}
      value={selectedOptions}
      onChange={(e) => {
        setSelectedOptions(e || []);

        if (e?.value || e.length > 0) {
          onChange({
            filterId: field,
            clause: "filter",
            filterKey: filterKey,
            query: {
              [term]: {
                [field]: isMulti ? e.map((item) => item.value) : e.value,
              },
            },
          });
        } else {
          onChange(null, field);
        }
      }}
    />
  );
}
