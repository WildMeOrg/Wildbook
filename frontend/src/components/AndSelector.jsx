import Select from "react-select";
import React from "react";
import { useEffect, useState } from "react";
import { useRef } from "react";
import { FormattedMessage } from "react-intl";
import { FormGroup, FormLabel } from "react-bootstrap";
import Description from "./Form/Description";

const colourStyles = {
  option: (styles) => ({
    ...styles,
    color: "black",
  }),
  singleValue: (styles) => ({ ...styles, color: "black" }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
  control: (base) => ({ ...base, zIndex: 1, backgroundColor: "white" }),
};

export default function AndSelector({
  noLabel,
  noDesc,
  label,
  isMulti,
  options,
  onChange,
  field,
  filterKey,
}) {
  const [selectedOptions, setSelectedOptions] = useState([]);
  const selectedOptionsRef = useRef(selectedOptions);

  useEffect(() => {
    onChange(null, field);
    return () => {
      options.forEach((option) => {
        onChange(null, `${field}.${option.value}`);
      });
    };
  }, []);

  const handleChange = (selected) => {
    const addedOptions = selected.filter(
      (option) => !selectedOptions.includes(option),
    );
    const removedOptions = selectedOptions.filter(
      (option) => !selected.includes(option),
    );

    setSelectedOptions(selected || []);
    selectedOptionsRef.current = selected || [];

    if (addedOptions.length > 0) {
      addedOptions.forEach((option) => {
        onChange({
          filterId: `${field}.${option.value}`,
          clause: "filter",
          filterKey: filterKey,
          query: {
            term: {
              [field]: option.value,
            },
          },
        });
      });
    }

    if (removedOptions.length > 0) {
      removedOptions.forEach((option) => {
        onChange(null, `${field}.${option.value}`);
      });
    }
  };

  return (
    <FormGroup className="mt-2">
      {noLabel ? null : (
        <FormLabel>
          <FormattedMessage id={label} defaultMessage={label} />
        </FormLabel>
      )}
      {noDesc ? null : (
        <Description>
          <FormattedMessage id={`${label}_DESC`} />
        </Description>
      )}

      <Select
        isMulti={isMulti}
        options={options}
        className="basic-multi-select"
        classNamePrefix="select"
        styles={colourStyles}
        menuPlacement="auto"
        menuPortalTarget={document.body}
        value={selectedOptions}
        onChange={handleChange}
      />
    </FormGroup>
  );
}
