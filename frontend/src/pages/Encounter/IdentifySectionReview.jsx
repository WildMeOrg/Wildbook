import React from 'react';
import { observer } from 'mobx-react-lite';
import { AttributesAndValueComponent } from '../../components/AttributesAndValueComponent';

export const IdentifySectionReview = observer(({ store }) => {
    return <div>
        <AttributesAndValueComponent
            attributeId="IDENTIFIED_AS"
            value={store.getFieldValue("identify", "individualDisplayName")}
        />
        <AttributesAndValueComponent
            attributeId="MATCHED_BY"
            value={store.getFieldValue("identify", "identificationRemarks")}
        />
        <AttributesAndValueComponent
            attributeId="ALTERNATE_ID"
            value={store.getFieldValue("identify", "otherCatalogNumbers")}
        />  
        <AttributesAndValueComponent
            attributeId="SIGHTING_ID"
            value={store.getFieldValue("identify", "occurrenceId")}
        />        
    </div>
})
