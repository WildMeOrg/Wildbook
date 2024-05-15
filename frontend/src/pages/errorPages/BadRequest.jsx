import React from "react";
import ErrorPage from "./ErrorPage";

export default function BadRequest() {
  return (
    <ErrorPage
      errorCode="400"
      errorId="ERROR_PAGE_BAD_REQUEST"
      errorDesc="ERROR_PAGE_BAD_REQUEST_DESC"
      position={[18, 35]}
    />
  );
}
