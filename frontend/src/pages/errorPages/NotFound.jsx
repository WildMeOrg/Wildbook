import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      statusCode="404"
      error_id="NOT_FOUND"
      error_desc="NOT_FOUND_DESC"
      position={[10, 55]}
    />
  );
}
