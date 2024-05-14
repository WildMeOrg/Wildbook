import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      statusCode="400"
      error_id="BAD_REQUEST"
      ERROR_DESC="BAD_REQUEST_DESC"
      position={[18, 35]}
    />
  );
}
