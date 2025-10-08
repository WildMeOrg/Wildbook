import React from 'react';
import { observer } from 'mobx-react-lite';
import MapDisplay from './MapDisplay';
import { AttributesAndValueComponent } from '../../components/AttributesAndValueComponent';

export const LocationSectionReview = observer(({ store }) => {
    return <div>
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
        <AttributesAndValueComponent
            attributeId="COORDINATES"
            value={(store.getFieldValue("location", "locationGeoPoint") &&
                Object.keys(store.getFieldValue("location", "locationGeoPoint")).length > 0)
                    ? `lat: ${store.getFieldValue("location", "locationGeoPoint")?.lat || ""}, lon: ${store.getFieldValue("location", "locationGeoPoint")?.lon || ""}`
                    : ""
            }
        />
        <div>
        </div>
        <MapDisplay store={store} />
    </div>
})
