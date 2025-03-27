import React from "react";
import Select from "react-select";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";
import { toJS } from "mobx";

const colourStyles = {
  option: (styles) => ({
    ...styles,
    color: "black",
  }),
  singleValue: (styles) => ({ ...styles, color: "black" }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
  control: (base) => ({ ...base, zIndex: 1, backgroundColor: "white" }),
};

const MultiSelect = observer(({ isMulti,
  options,
  field,
  filterKey,
  term,
  store
}) => {
  const intl = useIntl();

  const filterItem = toJS(store.formFilters).find((f) => f.filterId === field);
  const queryTerm = filterItem ? toJS(filterItem.query[term]) : {};
  const selectedValues = queryTerm ? queryTerm[field] : [];
  const selectedOptions = options.filter(option =>
    selectedValues?.some(value => value === option.value)
  );

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
      placeholder={intl.formatMessage({ id: "SELECT_ONE_OR_MORE" })}
      onChange={(e) => {
        const value = e?.value || e.map(item => item.value);
        if (e?.value || e.length > 0) {
          store.addFilter(field, "filter", {
            [term]: {
              [field]: value,
            },
          }, filterKey);
        } else {
          store.removeFilter(field);
        }
      }}
    />
  );
});

export default MultiSelect;