import React from "react";
import { observer } from "mobx-react-lite";
import MapDisplay from "./MapDisplay";
import { AttributesAndValueComponent } from "../../components/AttributesAndValueComponent";
import { FormattedMessage } from "react-intl";

export const LocationSectionReview = observer(({ store }) => {
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
        <h6>
          <FormattedMessage id="COORDINATES" />
        </h6>
        <div>
          {store.getFieldValue("location", "locationGeoPoint") &&
            `latitude: ${store.getFieldValue("location", "locationGeoPoint")?.lat || ""}`}
        </div>
        <div>
          {store.getFieldValue("location", "locationGeoPoint") &&
            `longitude: ${store.getFieldValue("location", "locationGeoPoint")?.lon || ""}`}
        </div>
      </div>
      <div>
        <h6>
          <FormattedMessage id="MAP" />
        </h6>
      </div>
      <div></div>
      <MapDisplay store={store} />
    </div>
  );
});
