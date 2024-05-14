import React from "react";
import ErrorPage from "./ErrorPage";

export default function NotFound() {
  return (
    <ErrorPage
      statusCode="500"
      error_id="SERVER_ERROR"
      error_desc="SERVER_ERROR_DESC"
      position={[20, 35]}
    />
  );
}
