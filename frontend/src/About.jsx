import React from "react";
import { Alert } from "react-bootstrap";
import FilterPanel from "./components/FilterPanel";

export default function About() {
  return (
    <div>
      <h1>About</h1>
      <Alert>This is a simple page about wildbook.</Alert>
      <FilterPanel />
    </div>
  );
}
