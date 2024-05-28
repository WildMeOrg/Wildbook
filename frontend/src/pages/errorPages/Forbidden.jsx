import React from "react";
import ErrorPage from "./ErrorPage";

export default function Forbidden() {
  return (
    <ErrorPage
      errorCode="403"
      errorId="ERROR_PAGE_FORBIDDEN"
      errorDesc="ERROR_PAGE_FORBIDDEN_DESC"
      position={[18, 25]}
    />
  );
}
