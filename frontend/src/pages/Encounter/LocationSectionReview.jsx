import React from "react";
import { observer } from "mobx-react-lite";
import MapDisplay from "./MapDisplay";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";

export const LocationSectionReview = observer(({ store }) => {
  const lat = store.getFieldValue("location", "locationGeoPoint")?.lat;
  const lon = store.getFieldValue("location", "locationGeoPoint")?.lon;
  return (
    <div>
      <AttributesAndValueComponent
        attributeId="LOCATION"
        value={store.getFieldValue("location", "verbatimLocality")}
      />
      <AttributesAndValueComponent
        attributeId="LOCATION_ID"
        value={store.getFieldValue("location", "locationName")}
      />
      <AttributesAndValueComponent
        attributeId="COUNTRY"
        value={store.getFieldValue("location", "country")}
      />
      <div>
        <AttributesAndValueComponent
          attributeId="LATITUDE"
          value={typeof lat === "number" ? lat : ""}
        />
        <AttributesAndValueComponent
          attributeId="LONGITUDE"
          value={typeof lon === "number" ? lon : ""}
        />
      </div>
      <MapDisplay store={store} />
    </div>
  );
});
