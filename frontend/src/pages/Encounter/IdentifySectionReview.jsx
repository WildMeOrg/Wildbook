import React from 'react';
import { observer } from 'mobx-react-lite';

export const IdentifySectionReview = observer(({ store }) => {
    return <div>
        <div>
            Identified as:{" "}
            {store.getFieldValue("identify", "individualDisplayName")}
        </div>
        <div>
            Matched by:{" "}
            {store.getFieldValue("identify", "identificationRemarks")}
        </div>
        <div>
            Alternate ID:{" "}
            {store.getFieldValue("identify", "otherCatalogNumbers")}
        </div>
    </div>
})
