import React from 'react';
import { observer } from 'mobx-react-lite';
import SelectInput from '../../components/generalInputs/SelectInput';
import { Alert } from "react-bootstrap";
import TextInput from '../../components/generalInputs/TextInput';
import CoordinatesInput from '../../components/generalInputs/CoordinatesInput';
import { Suspense, lazy } from "react";
import convertToTreeData from "../../utils/converToTreeData";


const TreeSelect = lazy(() => import("antd/es/tree-select"));
export const LocationSectionEdit = observer(({ store }) => {
    const locationOptions = convertToTreeData(store.siteSettingsData?.locationData?.locationID, "value", "label");

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
        {/* <SelectInput
            label="Location ID"
            value={
                store.getFieldValue("location", "locationId") ?? ""
            }
            onChange={(v) =>
                store.setFieldValue("location", "locationId", v)
            }
            options={store.locationIdOptions}
            className="mb-3"
        /> */}
        <label>Location</label>
        <Suspense fallback={<div>Loading location picker...</div>}>
            <TreeSelect
                id="location-tree-select"
                treeData={locationOptions}
                value={store.getFieldValue("location", "locationId") ?? ""}
                // treeCheckable
                // treeCheckStrictly
                // showCheckedStrategy="SHOW_ALL"
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
