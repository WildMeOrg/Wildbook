import React from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import { Alert } from "react-bootstrap";
import TextInput from '../../components/generalInputs/TextInput';
import CoordinatesInput from '../../components/generalInputs/CoordinatesInput';
import { Suspense, lazy } from "react";
import convertToTreeData from "../../utils/converToTreeData";
import { FormattedMessage } from 'react-intl';


const TreeSelect = lazy(() => import("antd/es/tree-select"));
export const LocationSectionEdit = observer(({ store }) => {
    const locationOptions = convertToTreeData(store.siteSettingsData?.locationData?.locationID, "value", "label");

    return <div>
        <TextInput
            label="LOCATION"
            value={
                store.getFieldValue("location", "verbatimLocality") ??
                ""
            }
            onChange={(v) =>
                store.setFieldValue("location", "verbatimLocality", v)
            }
        />
        <label><FormattedMessage id="LOCATION_ID"/></label>
        <Suspense fallback={<div>Loading location picker...</div>}>
            <TreeSelect
                id="location-tree-select"
                treeData={locationOptions}
                value={store.getFieldValue("location", "locationId") ?? ""}
                treeNodeFilterProp="value"
                treeLine
                showSearch
                size="large"
                allowClear
                style={{ width: "100%" }}
                placeholder="Select locations"
                dropdownStyle={{ maxHeight: "500px", zIndex: 9999 }}
                onChange={(val) =>
                    store.setFieldValue("location", "locationId", val)
                }
            />
        </Suspense>
        <SelectInput
            label="COUNTRY"
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
