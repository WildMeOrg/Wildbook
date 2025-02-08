import React from "react";
import Select from "react-select";
import { useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../models/useSearchQueryParams";
import { useStoredFormValue } from "../models/useStoredFormValue";
import { iteratee, remove } from "lodash-es";
import EncounterFormStore, { globalEncounterFormStore } from "../pages/SearchPages/encounterFormStore";
import { useLocalObservable } from "mobx-react-lite";
import { observer } from "mobx-react-lite";

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
  onChange,
  field,
  filterKey,
  term, }) => {
  const location = useLocation();
  const [selectedOptions, setSelectedOptions] = useState([]);
  const navigate = useNavigate();
  const intl = useIntl();

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


  return (
    <Select
      isMulti={isMulti}
      options={[
        {
          value: "all",
          label: "all",
        },
        {
          value: "none",
          label: "none",
        }
      ]}
      className="basic-multi-select"
      classNamePrefix="select"
      styles={colourStyles}
      menuPlacement="auto"
      menuPortalTarget={document.body}
      placeholder={intl.formatMessage({ id: "SELECT_ONE_OR_MORE" })}
      value={globalEncounterFormStore.formFilters.find((f) => f.filterKey === filterKey)?.query[term][field]}
      onChange={(e) => {
        const value = e?.value || e.map(item => item.value);
        if (e?.value || e.length > 0) {
          globalEncounterFormStore.addFilter(field, e, filterKey, term, field);
        } else {
          globalEncounterFormStore.removeFilter(field);
        }
      }}
    />
  );
});

export default MultiSelect;