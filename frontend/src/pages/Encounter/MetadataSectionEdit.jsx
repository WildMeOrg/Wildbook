import React from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import { Alert } from "react-bootstrap";

export const MetadataSectionEdit = observer(({ store }) => {
    return <div>
        <div>Encounter ID: {store.encounterData?.id}</div>
        <div>Date Created: {store.encounterData?.createdAt}</div>
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
                ""
            )}
        </div>
        <SelectInput
            label="Assigned User"
            value={
                store.getFieldValue("metadata", "submitterID") ?? ""
            }
            onChange={(v) =>
                store.setFieldValue("metadata", "submitterID", v)
            }
            options={
                store.siteSettingsData?.users
                    ?.filter((item) => item.username)
                    .map((item) => {
                        return {
                            value: item.username,
                            label: item.username,
                        };
                    }) || []
            }
            className="mb-3"
        />
        {store.errors.hasSectionError("metadata") && (
            <Alert variant="danger">
                {store.errors.getSectionErrors("metadata").join(";")}
            </Alert>
        )}
    </div>
})
