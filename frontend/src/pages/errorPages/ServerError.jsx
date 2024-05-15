import React from "react";
import ErrorPage from "./ErrorPage";

export default function ServerError() {
  return (
    <ErrorPage
      errorCode="500"
      errorId="ERROR_PAGE_SERVER_ERROR"
      errorDesc="ERROR_PAGE_FORBIDDEN_DESC"
      position={[21, 30]}
    />
  );
}
