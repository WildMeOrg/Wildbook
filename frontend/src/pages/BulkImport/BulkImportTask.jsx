import React from "react";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";


export const BulkImportTask = observer(({ store }) => {
    const handleClick = () => {
        console.log("Bulk Import Task button clicked");
    };

    return (
        <div className="d-flex flex-column">
            <h2>
                <FormattedMessage id="BULK_IMPORT_TASK" />
            </h2>
            <button onClick={handleClick}>
                <FormattedMessage id="BULK_IMPORT_TASK_BUTTON" />
            </button>
        </div>
    );
});