import React from "react";
import { observer } from "mobx-react-lite";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";
import { formatDateValues } from "./stores/helperFunctions";

export const DateSectionReview = observer(({ store }) => {
  return (
    <div>
      <AttributesAndValueComponent
        attributeId="DATE"
        value={(() => {
          const iso = store.getFieldValue("date", "dateValues");
          return formatDateValues(iso);
        })()}
      />
      <AttributesAndValueComponent
        attributeId="VERBATIM_EVENT_DATE"
        value={store.getFieldValue("date", "verbatimEventDate")}
      />
    </div>
  );
});
