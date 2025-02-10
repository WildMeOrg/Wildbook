import React from "react";
import Select from "react-select";
import { useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../models/useSearchQueryParams";
import { useStoredFormValue } from "../models/useStoredFormValue";
import { iteratee, remove } from "lodash-es";
import EncounterFormStore, { store } from "../pages/SearchPages/encounterFormStore";
import { useLocalObservable } from "mobx-react-lite";
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
  const location = useLocation();
  // const [selectedOptions, setSelectedOptions] = useState([]);
  const navigate = useNavigate();
  const intl = useIntl();

  // const selectedValues = JSON.stringify(store.formFilters.find((f) => f.filterKey === filterKey)?.query[term][field]);
  //   console.log("555555555555555555", selectedValues);
  // const selectedOptions = options.filter(option => 
  //   selectedValues.some(value => value === option.value)
  // );

  const filterItem = store.formFilters.find((f) => f.filterKey === filterKey);
  const queryTerm = filterItem ? toJS(filterItem.query[term]) : {};
  const selectedValues = queryTerm ? queryTerm[field] : [];

  // const store = useLocalObservable(() => new EncounterFormStore());

  // const paramsObject = useSearchQueryParams();
  // const resultValue = useStoredFormValue(field, term, field);

  // useEffect(() => {
  //   if (paramsObject.searchQueryId && resultValue) {
  //     setSelectedOptions(
  //       resultValue.map((item) => ({ value: item, label: item })),
  //     );
  //   }
  // }, [paramsObject, resultValue]);

  // useEffect(() => {
  //   const params = new URLSearchParams(location.search);
  //   if (field === "assignedUsername") {
  //     const fieldValue = params.get("username");
  //     if (fieldValue) {
  //       const selectedItems = options.filter(
  //         (option) => fieldValue === option.label,
  //       );
  //       setSelectedOptions(selectedItems);
  //     }
  //   } else if (field === "state") {
  //     const fieldValue = params.get("state");
  //     if (fieldValue) {
  //       const selectedItems = options.filter(
  //         (option) => fieldValue === option.label,
  //       );
  //       setSelectedOptions(selectedItems);
  //     }
  //   }
  // }, [location.search, field, options, isMulti]);

  // console.log("111111111111111", JSON.stringify(store.formFilters.find((f) => f.filterKey === filterKey)?.query[term][field]));


  return (
    <Select
      isMulti={isMulti}
      options={options}
      className="basic-multi-select"
      classNamePrefix="select"
      styles={colourStyles}
      menuPlacement="auto"
      menuPortalTarget={document.body}
      placeholder={intl.formatMessage({ id: "SELECT_ONE_OR_MORE" })}
      // value={selectedOptions}
      onChange={(e) => {
        const value = e?.value || e.map(item => item.value);
        if (e?.value || e.length > 0) {
          store.addFilter(field, "filter",{
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