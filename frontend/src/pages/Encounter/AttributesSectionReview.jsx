import React from 'react';
import { observer } from 'mobx-react-lite';

export const AttributesSectionReview = observer(({ store }) => {
    return <div>
        <div>
            Taxonomy: {store.getFieldValue("attributes", "taxonomy")}
        </div>
        <div>
            Status:{" "}
            {store.getFieldValue("attributes", "livingStatus")}
        </div>
        <div>Sex: {store.getFieldValue("attributes", "sex")}</div>
        <div>
            Noticeable Scarring:{" "}
            {store.getFieldValue("attributes", "distinguishingScar")}
        </div>
        <div>
            Behavior: {store.getFieldValue("attributes", "behavior")}
        </div>
        <div>
            Group Role:{" "}
            {store.getFieldValue("attributes", "groupRole")}
        </div>
        <div>
            Patterning Code:{" "}
            {store.getFieldValue("attributes", "patterningCode")}
        </div>
        <div>
            Life Stage:{" "}
            {store.getFieldValue("attributes", "lifeStage")}
        </div>
        <div>
            Observation Comments:{" "}
            {store.getFieldValue("attributes", "occurrenceRemarks")}
        </div>
    </div>
})
