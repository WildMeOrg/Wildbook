import React from 'react';
import { observer } from 'mobx-react-lite';
import TextInput from '../../components/generalInputs/TextInput';
import SelectInput from '../../components/generalInputs/SelectInput';
import SearchAndSelectInput from '../../components/generalInputs/SearchAndSelectInput';
import { Alert } from "react-bootstrap";

export const IdentifySectionEdit = observer(({ store }) => {
    return <div>
        <SelectInput
            label="MATCHED_BY"
            value={
                store.getFieldValue(
                    "identify",
                    "identificationRemarks",
                ) ?? ""
            }
            onChange={(v) =>
                store.setFieldValue(
                    "identify",
                    "identificationRemarks",
                    v,
                )
            }
            options={store.identificationRemarksOptions}
            className="mb-3"
        />

        <SearchAndSelectInput
            label="INDIVIDUAL_ID"
            value={
                store.getFieldValue("identify", "individualDisplayName") ?? ""
            }
            onChange={(v) => {
                const label = store.individualOptions.find((opt) => opt.value === v)?.label;
                store.setFieldValue("identify", "individualDisplayName", label);
            }
            }
            options={[]}
            loadOptions={async (q) => {
                const resp = await store.searchIndividualsByName(q);
                const options = resp.data.hits.map((it) => ({
                    value: String(it.id),
                    label: it.displayName,
                }));
                store.setIndividualOptions(options);
                return options;
            }}
            debounceMs={300}
            minChars={2}
        />

        <TextInput
            label="ALTERNATE_ID"
            value={
                store.getFieldValue(
                    "identify",
                    "otherCatalogNumbers",
                ) ?? ""
            }
            onChange={(v) =>
                store.setFieldValue(
                    "identify",
                    "otherCatalogNumbers",
                    v,
                )
            }
        />
        <SearchAndSelectInput
            label="SIGHTING_ID"
            value={
                store.getFieldValue("identify", "occurrenceId") ?? ""
            }
            onChange={(v) =>
                store.setFieldValue("identify", "occurrenceId", v)
            }
            options={[]}
            loadOptions={async (q) => {
                const resp = await store.searchSightingsById(q);
                return (
                    resp.data?.items?.map((it) => ({
                        value: String(it.id),
                        label: it.displayName,
                    })) ?? []
                );
            }}
            debounceMs={300}
            minChars={2}
        />
        {store.errors.hasSectionError("identify") && (
            <Alert variant="danger">
                {store.errors.getSectionErrors("identify").join(";")}
            </Alert>
        )}
    </div>
})
