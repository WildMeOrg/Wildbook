import React from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import { Alert } from "react-bootstrap";
import TextInput from '../../components/generalInputs/TextInput';
import CoordinatesInput from '../../components/generalInputs/CoordinatesInput';

export const LocationSectionEdit = observer(({ store }) => {
    return <div>
        <TextInput
            label="Location"
            value={
                store.getFieldValue("location", "verbatimLocality") ??
                ""
            }
            onChange={(v) =>
                store.setFieldValue("location", "verbatimLocality", v)
            }
        />
        <SelectInput
            label="Location ID"
            value={
                store.getFieldValue("location", "locationId") ?? ""
            }
            onChange={(v) =>
                store.setFieldValue("location", "locationId", v)
            }
            options={store.locationIdOptions}
            className="mb-3"
        />
        <SelectInput
            label="Country"
            value={store.getFieldValue("location", "country") ?? ""}
            onChange={(v) =>
                store.setFieldValue("location", "country", v)
            }
            options={
                store.siteSettingsData?.country?.map((item) => {
                    return {
                        value: item,
                        label: item,
                    };
                }) || []
            }
            className="mb-3"
        />
        <CoordinatesInput store={store} />
        {store.errors.hasSectionError("location") && (
            <Alert variant="danger">
                {store.errors.getSectionErrors("location").join(";")}
            </Alert>
        )}
    </div>
})
