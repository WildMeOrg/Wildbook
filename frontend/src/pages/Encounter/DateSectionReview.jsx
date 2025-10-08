import React from 'react';
import { observer } from 'mobx-react-lite';
import { AttributesAndValueComponent } from '../../components/AttributesAndValueComponent';

export const DateSectionReview = observer(({ store }) => {
    return <div>
        <AttributesAndValueComponent
            attributeId="DATE"
            value={store.getFieldValue("date", "date")}
        />
        <AttributesAndValueComponent
            attributeId="VERBATIM_EVENT_DATE"
            value={store.getFieldValue("date", "verbatimEventDate")}
        />
    </div>
})
