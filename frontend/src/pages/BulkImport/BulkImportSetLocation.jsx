import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";

export const BulkImportSetLocation = observer(({ store }) => {

    console.log("BulkImportSetLocation component rendered");

    return (
        <div className="d-flex flex-column">
            <h2>
                <FormattedMessage id="BULK_IMPORT_SET_LOCATION" />
            </h2>
            {store.bulkImportId}

        </div>
    );
});