import React from "react";
import { FormattedMessage } from "react-intl";

export const AttributesAndValueComponent = ({ attributeId, value }) => {
  return (
    <div className="mb-2 d-flex align-items-baseline gap-1">
      <h6 className="mb-0 me-1">
        <FormattedMessage id={attributeId} />
        {":"}
      </h6>
      <p className="mb-0">{value}</p>
    </div>
  );
};
