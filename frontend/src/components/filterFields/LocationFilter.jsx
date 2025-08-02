import React from "react";
import LocationFilterMap from "./LocationFilterMap";
import LocationFilterText from "./LocationFilterText";

export default function LocationFilter({ data, store }) {
  return (
    <div>
      <LocationFilterMap data={data} store={store}/>
      <LocationFilterText  store={store}/>
    </div>
  );
}
