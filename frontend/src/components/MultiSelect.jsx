import React from "react";
import Select from "react-select";
import { useLocation, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../models/useSearchQueryParams";
import { useStoredFormValue } from "../models/useStoredFormValue";
import { remove } from "lodash-es";

const colourStyles = {
  option: (styles) => ({
    ...styles,
    color: "black",
  }),
  singleValue: (styles) => ({ ...styles, color: "black" }),
  menuPortal: (base) => ({ ...base, zIndex: 9999 }),
  control: (base) => ({ ...base, zIndex: 1, backgroundColor: "white" }),
};

export default function MultiSelect({
  isMulti,
  options,
  onChange,
  field,
  filterKey,
  term,
}) {
  const location = useLocation();
  const [selectedOptions, setSelectedOptions] = useState([]);
  const navigate = useNavigate();
  const intl = useIntl();

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
      options={options}
      className="basic-multi-select"
      classNamePrefix="select"
      styles={colourStyles}
      menuPlacement="auto"
      menuPortalTarget={document.body}
      placeholder={intl.formatMessage({ id: "SELECT_ONE_OR_MORE" })}
      value={selectedOptions}
      onChange={(e) => {
        // const params = new URLSearchParams(location.search);
        // if (field === "assignedUsername") {
        //   params.delete("username");
        //   onChange(null, "assignedUsername");
        //   navigate(`${location.pathname}?${params.toString()}`, {
        //     replace: true,
        //   });
        // } else if (field === "state") {
        //   params.delete("state");
        //   onChange(null, "state");
        //   navigate(`${location.pathname}?${params.toString()}`, {
        //     replace: true,
        //   });
        // }

        // setSelectedOptions(e || []);

        if (e?.value || e.length > 0) {
          addFilter(e, field, filterKey);
        } else {
          removeFilter(field);
        }
      }}
    />
  );
}
