import React from "react";
import { observer } from "mobx-react-lite";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";
import { FormattedMessage } from "react-intl";

export const IdentifySectionReview = observer(({ store }) => {
  return (
    <div>
      <div className="mb-2 d-flex align-items-baseline gap-1">
        <h6 className="mb-0 me-1">
          <FormattedMessage id="IDENTIFIED_AS" />
          {":"}
        </h6>
        {!!store.getFieldValue("identify", "individualDisplayName") && (
          <a
            href={`/individuals.jsp?id=${store.getFieldValue("identify", "individualId")}`}
            target="_blank"
            rel="noopener noreferrer"
            className="text-decoration-none"
          >
            {store.getFieldValue("identify", "individualDisplayName")}
          </a>
        )}
      </div>
      <AttributesAndValueComponent
        attributeId="MATCHED_BY"
        value={store.getFieldValue("identify", "identificationRemarks")}
      />
      <AttributesAndValueComponent
        attributeId="ALTERNATE_ID"
        value={store.getFieldValue("identify", "otherCatalogNumbers")}
      />
      <div className="mb-2 d-flex align-items-baseline gap-1">
        <h6 className="mb-0 me-1">
          <FormattedMessage id="SIGHTING_ID" />
          {":"}
        </h6>
        <a
          href={`/occurrence.jsp?number=${store.getFieldValue("identify", "occurrenceId")}`}
          target="_blank"
          rel="noopener noreferrer"
          className="text-decoration-none"
        >
          {store.getFieldValue("identify", "occurrenceId")}
        </a>
      </div>
    </div>
  );
});
