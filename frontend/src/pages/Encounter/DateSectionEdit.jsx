import React from 'react';
import { observer } from 'mobx-react-lite';
import DateInput from '../../components/generalInputs/DateInput';
import TextInput from '../../components/generalInputs/TextInput';
import { Alert } from "react-bootstrap";

export const DateSectionEdit = observer(({ store }) => {
    return <div>
        <DateInput
            label="Encounter Date"
            value={store.getFieldValue("date", "date") ?? null}
            onChange={(v) => {
                store.setFieldValue("date", "date", v);
            }}
            className="mb-3"
        />
        {store.errors.getFieldError("date", "date") && (
            <div className="invalid-feedback d-block">
                {store.errors.getFieldError("date", "date")}
            </div>
        )}
        <TextInput
            label="Verbatim Event Date"
            value={
                store.getFieldValue("date", "verbatimEventDate") ?? ""
            }
            onChange={(v) =>
                store.setFieldValue("date", "verbatimEventDate", v)
            }
        />
        {store.errors.hasSectionError("date") && (
            <Alert variant="danger">
                {store.errors.getSectionErrors("date").join(";")}
            </Alert>
        )}

    </div>
})
