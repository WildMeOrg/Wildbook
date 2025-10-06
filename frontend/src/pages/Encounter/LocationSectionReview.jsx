import React from 'react';
import { observer } from 'mobx-react-lite';
import MapDisplay from './MapDisplay';

export const LocationSectionReview = observer(({ store }) => {
    return <div>
        <div>
            Location:{" "}
            {store.getFieldValue("location", "verbatimLocality") ||
                "None"}
        </div>
        
        <div>
            Location ID:{" "}
            {store.getFieldValue("location", "locationName") ||
                "None"}
        </div>
        <div>
            Country:{" "}
            {store.getFieldValue("location", "country") || "None"}
        </div>
        <MapDisplay store={store} />
    </div>
})
