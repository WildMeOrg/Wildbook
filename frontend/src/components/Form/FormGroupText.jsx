import React from "react";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import Description from "./Description";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";

export default function FormGroupText({
  noLabel = false,
  noDesc = false,
  label = "",
  onChange,
  filterId,
  field,
  term,
  filterKey,
}) {
  const intl = useIntl();
  return (
    <FormGroup className="mt-2">
      {!noLabel && (
        <FormLabel>
          <FormattedMessage id={label} defaultMessage="" />
        </FormLabel>
      )}
      {!noDesc && (
        <Description>
          <FormattedMessage id={`${label}_DESC`} />
        </Description>
      )}
      <FormControl
        type="text"
        placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
        onChange={(e) => {
          if (e.target.value === "") {
            onChange(null, field);
            return;
          }
          onChange({
            filterId: filterId,
            clause: "filter",
            filterKey: filterKey,
            query: {
              [term]: {
                [field]: e.target.value,
              },
            },
          });
        }}
      />
    </FormGroup>
  );
}
