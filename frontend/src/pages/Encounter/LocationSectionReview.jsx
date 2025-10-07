import React from 'react';
import { observer } from 'mobx-react-lite';
import MapDisplay from './MapDisplay';

export const LocationSectionReview = observer(({ store }) => {
    return <div>
        <div>
            Location:{" "}
            {store.getFieldValue("location", "verbatimLocality")}
        </div>
        
        <div>
            Location ID:{" "}
            {store.getFieldValue("location", "locationName")}
        </div>
        <div>
            Country:{" "}
            {store.getFieldValue("location", "country")}
        </div>
        <div>
            Coordinates:{" "}
            {/* {Object.keys(store.getFieldValue("location", "locationGeoPoint")).length ? `lat: ${store.getFieldValue("location", "locationGeoPoint")?.lat}, lon: ${store.getFieldValue("location", "locationGeoPoint")?.lon}` : ""} */}
        </div>
        <div>
        </div>
        <MapDisplay store={store} />
    </div>
})
