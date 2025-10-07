import React from 'react';
import { observer } from 'mobx-react-lite';

export const DateSectionReview = observer(({ store }) => {
    return <div>
        <div>
            Encounter Date:{" "}
            {store.getFieldValue("date", "date")}
        </div>
        <div>
            Verbatim Event Date:{" "}
            {store.getFieldValue("date", "verbatimEventDate")}
        </div>
    </div>
})
