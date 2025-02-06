import React, { useEffect } from "react";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import Description from "./Description";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../../models/useSearchQueryParams";
import { useStoredFormValue } from "../../models/useStoredFormValue";

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
  const [value, setValue] = React.useState("");
  const paramsObject = useSearchQueryParams();
  const resultValue = useStoredFormValue(filterId, term, field);

  useEffect(() => {
    if (paramsObject.searchQueryId && resultValue) {
      setValue(resultValue);
    }
  }, [paramsObject, resultValue]);

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
        value={value}
        onChange={(e) => {
          if (e.target.value === "") {
            onChange(null, field);
            return;
          }
          setValue(e.target.value);
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
