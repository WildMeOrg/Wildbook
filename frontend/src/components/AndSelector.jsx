import Select from "react-select";
import React from "react";
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
  field,
  filterKey,
  store,
  value,
}) {
  const valuesSet = new Set(value);
  const selectedOptions = options.filter(item => valuesSet.has(item.value));
  console.log("selectedOptions", selectedOptions);

  const handleChange = (selected) => {
    store.removeFilterByFilterKey(filterKey);

    selected.forEach((option) => {
      store.addFilter(
        `${field}.${option.value}`,
        "filter",
        {
          term: {
            [field]: option.value,
          },
        },
        filterKey,
      );
    });
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
