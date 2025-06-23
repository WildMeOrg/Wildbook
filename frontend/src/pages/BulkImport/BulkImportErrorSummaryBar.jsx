import React from "react";
import { observer } from "mobx-react-lite";
import Badge from "react-bootstrap/Badge";

const ErrorSummaryBar = observer(({ store }) => {
  const errors = store.validationErrors || {};

  let error = 0;
  let missingField = 0;

  Object.values(errors).forEach((rowErrors) => {
    Object.values(rowErrors).forEach((errMsg) => {
      if (/required/i.test(errMsg)) {
        missingField += 1;
      } else if (/invalid/i.test(errMsg)) {
        error += 1;
      }
    });
  });

  return (
    <div className="d-flex gap-2 py-2">
      <Badge bg="danger">Error: {error}</Badge>

      <Badge bg="warning" text="dark">
        Missing Field: {missingField}
      </Badge>

      <Badge bg="primary">
        Empty Field: {store.emptyFieldCount}
      </Badge>

    </div>
  );
});

export default ErrorSummaryBar;
