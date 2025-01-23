import React from "react";
import LocationFilterMap from "./LocationFilterMap";
import LocationFilterText from "./LocationFilterText";

export default function LocationFilter({ onChange, data }) {
  return (
    <div>
      <LocationFilterMap onChange={onChange} data={data} />
      <LocationFilterText onChange={onChange} />
    </div>
  );
}
