import React from "react";
import { observer } from "mobx-react-lite";
import Badge from "react-bootstrap/Badge";

const ErrorSummaryBar = observer(({ store }) => {
  const { error, missingField, emptyField, imgVerifyPending } = store.errorSummary;

  return (
    <div className="d-flex gap-2 py-2">
      <Badge bg="danger">Error: {error}</Badge>

      <Badge bg="warning" text="dark">
        Missing Field: {missingField}
      </Badge>

      <Badge bg="primary">
        Empty Field: {emptyField}
      </Badge>

      {/* <Badge bg="secondary">
        Image Verification&nbsp;Pending{imgVerifyPending ? `: ${imgVerifyPending}` : ""}
      </Badge> */}
    </div>
  );
});

export default ErrorSummaryBar;
