import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      statusCode="403"
      error_id="FORBIDDEN"
      error_desc="FORBIDDEN_DESC"
      position={[18, 45]}
    />
  );
}
