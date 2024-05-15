import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      errorCode="404"
      errorId="ERROR_PAGE_NOT_FOUND"
      errorDesc="ERROR_PAGE_NOT_FOUND_DESC"
      position={[10, 45]}
    />
  );
}
