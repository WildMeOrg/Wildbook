import React from 'react';
import { observer } from 'mobx-react-lite';
import { AttributesAndValueComponent } from '../../components/AttributesAndValueComponent';

export const AttributesSectionReview = observer(({ store }) => {
    return <div>
        <AttributesAndValueComponent
            attributeId="TAXONOMY"
            value={store.getFieldValue("attributes", "taxonomy")}
        />
        <AttributesAndValueComponent
            attributeId="STATUS"
            value={store.getFieldValue("attributes", "livingStatus")}
        />
        <AttributesAndValueComponent
            attributeId="SEX"
            value={store.getFieldValue("attributes", "sex")}
        />
        <AttributesAndValueComponent
            attributeId="DISTINGUISHING_SCAR"
            value={store.getFieldValue("attributes", "distinguishingScar")}
        />
        <AttributesAndValueComponent
            attributeId="BEHAVIOR"
            value={store.getFieldValue("attributes", "behavior")}
        />
        <AttributesAndValueComponent
            attributeId="GROUP_ROLE"
            value={store.getFieldValue("attributes", "groupRole")}
        />
        <AttributesAndValueComponent
            attributeId="PATTERNING_CODE"
            value={store.getFieldValue("attributes", "patterningCode")}
        />
        <AttributesAndValueComponent
            attributeId="LIFE_STAGE"
            value={store.getFieldValue("attributes", "lifeStage")}
        />
        <AttributesAndValueComponent
            attributeId="OBSERVATION_COMMENTS"
            value={store.getFieldValue("attributes", "occurrenceRemarks")}
        />
    </div>
})
