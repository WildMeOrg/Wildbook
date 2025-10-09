import React from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import { Alert } from "react-bootstrap";
import { FormattedMessage } from 'react-intl';

export const MetadataSectionEdit = observer(({ store }) => {
    return <div>
        <div><FormattedMessage id="ENCOUNTER_ID"/>: {store.encounterData?.id}</div>
        <div><FormattedMessage id="DATE_CREATED"/>: {store.encounterData?.createdAt}</div>
        <div>
            <FormattedMessage id="LAST_EDIT"/>:{" "}
            {store.encounterData?.version
                ? new Date(store.encounterData.version).toLocaleString()
                : "None"}
        </div>
        <div>
            <FormattedMessage id="IMPORTED_VIA"/>:{" "}
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
            label="ASSIGNED_USER"
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
