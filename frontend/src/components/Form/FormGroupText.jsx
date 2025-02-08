import React, { useEffect, useState } from "react";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import Description from "./Description";
import { FormattedMessage } from "react-intl";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../../models/useSearchQueryParams";
import { useStoredFormValue } from "../../models/useStoredFormValue";
import { globalEncounterFormStore } from "../../pages/SearchPages/encounterFormStore";
import { useLocalObservable } from "mobx-react-lite";
import { observer } from "mobx-react-lite";

const FormGroupText = observer(({
  noLabel = false,
  noDesc = false,
  label = "",
  filterId,
  field,
  term,
  filterKey,
}) => {
  const intl = useIntl();
  // const store = useLocalObservable(() => new EncounterFormStore());
  const paramsObject = useSearchQueryParams();
  const resultValue = useStoredFormValue(field, term, field);
  const [value, setValue] = useState("");

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
        value={globalEncounterFormStore.formFilters.find((f) => f.filterId === filterId)?.query[term][field]}
        onChange={(e) => {
          setValue(e.target.value);
          if (e.target.value === "") {
            globalEncounterFormStore.removeFilter(filterId);
            return;
          }
          globalEncounterFormStore.addFilter(field, e.target.value, filterKey, term, filterId);          
        }}
      />
    </FormGroup>
  );
}
);

export default FormGroupText;
