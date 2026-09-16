import React from "react";
import { observer } from "mobx-react-lite";
import DateInput from "../../components/generalInputs/DateInput";
import TextInput from "../../components/generalInputs/TextInput";
import { Alert } from "react-bootstrap";
import { formatDateValues } from "./stores/helperFunctions";
import { useIntl } from "react-intl";

export const DateSectionEdit = observer(({ store }) => {
  const intl = useIntl();
  return (
    <div>
      <DateInput
        label="ENCOUNTER_DATE"
        value={(() => {
          const iso = store.getFieldValue("date", "dateValues");
          if (iso && typeof iso === "string") {
            return iso;
          } else if (iso && typeof iso === "object") {
            return formatDateValues(iso);
          }
        })()}
        onChange={(v) => {
          store.setFieldValue("date", "dateValues", v);
        }}
        className="mb-3"
      />
      {store.errors.getFieldError("date", "dateValues") && (
        <div className="invalid-feedback d-block">
          {intl.formatMessage({
            id: store.errors.getFieldError("date", "dateValues"),
          })}
        </div>
      )}

      <TextInput
        label="VERBATIM_EVENT_DATE"
        value={store.getFieldValue("date", "verbatimEventDate") ?? ""}
        onChange={(v) => store.setFieldValue("date", "verbatimEventDate", v)}
      />
      {store.errors.hasSectionError("date") && (
        <Alert variant="danger">
          {store.errors.getSectionErrors("date").join(";")}
        </Alert>
      )}
    </div>
  );
});
