import React from "react";
import { ProgressBar } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";

export const BulkImportProgress = observer(({ store }) => {
    return (
        <div className="p-2">
            <h5 style={{ fontWeight: "600" }}>
                <FormattedMessage id="BULK_IMPORT_PROGRESS" />
            </h5>
            <div className="d-flex flex-column">
                {store.flow && (
                    <ProgressBar
                        now={store.flow.progress()}
                        label={`${store.flow.progress()}%`}
                        striped
                        variant="success"
                    />
                )}
            </div>
        </div>
    );
});
