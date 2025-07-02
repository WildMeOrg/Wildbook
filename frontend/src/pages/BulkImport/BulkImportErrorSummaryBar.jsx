import React from "react";
import { observer } from "mobx-react-lite";
import Badge from "react-bootstrap/Badge";
import { FormattedMessage } from "react-intl";

const ErrorSummaryBar = observer(({ store }) => {
  const errors = store.validationErrors || {};

  let error = 0;
  let missingField = 0;

  Object.values(errors).forEach((rowErrors) => {
    Object.values(rowErrors).forEach((errMsg) => {
      if (/required/i.test(errMsg)) {
        missingField += 1;
      } else if (/invalid/i.test(errMsg) || /missing/i.test(errMsg)) {
        error += 1;
      }
    });
  });

  return (
    <div className="d-flex gap-2 py-2" id="bulk-import-error-summary-bar">
      <Badge bg="danger">
        <FormattedMessage id="BULK_IMPORT_ERROR" /> {": "} {error}
      </Badge>

      <Badge
        bg="danger"
        // text="dark"
      >
        <FormattedMessage id="BULK_IMPORT_MISSING_FIELD" /> {": "}{" "}
        {missingField}
      </Badge>

      <Badge bg="primary">
        <FormattedMessage id="BULK_IMPORT_EMPTY_FIELD" /> {": "}{" "}
        {store.emptyFieldCount}
      </Badge>
    </div>
  );
});

export default ErrorSummaryBar;
