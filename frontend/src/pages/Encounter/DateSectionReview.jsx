import React from "react";
import { observer } from "mobx-react-lite";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";

export const DateSectionReview = observer(({ store }) => {
  return (
    <div>
      <AttributesAndValueComponent
        attributeId="DATE"
        value={(() => {
          const iso = store.getFieldValue("date", "date");
          return iso
            ? iso
                .replace("T", " ")
                .replace(/\.\d+/, "")
                .replace(/Z|([+-]\d{2}:\d{2})$/, "")
                .trim()
            : "";
        })()}
      />
      <AttributesAndValueComponent
        attributeId="VERBATIM_EVENT_DATE"
        value={store.getFieldValue("date", "verbatimEventDate")}
      />
    </div>
  );
});
