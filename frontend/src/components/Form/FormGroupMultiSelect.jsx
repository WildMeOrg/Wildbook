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
  loading = false,
}) {
  const isDisabled = loading;

  const displayOptions = loading
    ? [
        {
          label: "Loading…",
          value: "__loading__",
          isDisabled: true,
        },
      ]
    : options;

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
        options={displayOptions}
        isMulti={isMulti}
        term={term}
        field={field}
        filterKey={filterKey}
        store={store}
        disabled={isDisabled}
        placeholder={loading ? "Loading…" : undefined}
        noOptionsMessage={() => (loading ? "Loading…" : "No options")}
      />
    </FormGroup>
  );
}
