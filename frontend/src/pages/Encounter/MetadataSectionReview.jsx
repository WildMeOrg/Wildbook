import React from 'react';
import { observer } from 'mobx-react-lite';

export const MetadataSectionReview = observer(({ store }) => {
    return <div>
        <div>Encounter ID: {store.encounterData?.id}</div>
        <div>
            Date Created: {store.encounterData?.dateSubmitted}
        </div>
        <div>
            Last Edit:{" "}
            {store.encounterData?.version
                ? new Date(store.encounterData.version).toLocaleString()
                : "None"}
        </div>
        <div>
            Imported via:{" "}
            {store.encounterData?.importTaskId ? (
                <a
                    href={`/react/bulk-import-task?id=${store.encounterData.importTaskId}`}
                    target="_blank"
                    rel="noopener noreferrer"
                >
                    {store.encounterData.importTaskId}
                </a>
            ) : (
                "none"
            )}
        </div>
        <div>
            Assigned User:{" "}
            {store.getFieldValue("metadata", "assignedUsername") ||
                "None"}
        </div>
    </div>
})
