import React from "react";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import Description from "./Description";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const FormGroupText = observer(({
  noLabel = false,
  noDesc = false,
  label = "",
  filterId,
  field,
  term,
  filterKey,
  store,
}) => {
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
        value={store.formFilters.find((f) => f.filterId === filterId)?.query[term][field]}
        onChange={(e) => {
          // setValue(e.target.value);
          if (e.target.value === "") {
            store.removeFilter(filterId);
            return;
          }
          store.addFilter(field, "filter", {
            [term]: {
              [field]: e.target.value,
            },
          }, filterKey);          
        }}
      />
    </FormGroup>
  );
}
);

export default FormGroupText;
