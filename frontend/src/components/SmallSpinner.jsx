import React from "react";
import { Spinner } from "react-bootstrap";

const SmallSpinner = () => (
  <Spinner
    animation="border"
    size="sm"
    role="status"
    aria-label="loading"
    style={{ width: 16, height: 16 }}
  />
);
export default SmallSpinner;
