import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      statusCode="401"
      error_id="UNAUTHORIZED"
      error_desc="UNAUTHORIZED_DESC"
      position={[25, 55]}
    />
  );
}
