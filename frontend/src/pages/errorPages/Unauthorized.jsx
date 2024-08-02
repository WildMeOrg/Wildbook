import React from "react";
import ErrorPage from "./ErrorPage";

export default function Unauthorized({
  setHeader
}) {
  setHeader(false);
  return (
    <ErrorPage
      errorCode="401"
      errorId="ERROR_PAGE_UNAUTHORIZED"
      errorDesc="ERROR_PAGE_UNAUTHORIZED_DESC"
      loginRequired={true}
      position={[15, 40]}
    />
  );
}
