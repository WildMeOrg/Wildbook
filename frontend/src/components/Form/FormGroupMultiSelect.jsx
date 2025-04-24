import React from "react";
import MultiSelect from "../MultiSelect";
import Description from "./Description";
import { FormattedMessage } from "react-intl";
import { FormGroup, FormLabel } from "react-bootstrap";

export default function FormGroupMultiSelect({
  isMulti = false,
  noLabel,
  noDesc,
  label = "",
  options = [],
  term = "terms",
  field = "field",
  filterKey,
  store,
}) {
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

      <MultiSelect
        options={options}
        isMulti={isMulti}
        term={term}
        field={field}
      
        filterKey={filterKey}
        store={store}
      ></MultiSelect>
    </FormGroup>
  );
}
