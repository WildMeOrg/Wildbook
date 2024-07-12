import React, { useState } from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl, FormSelect } from 'react-bootstrap';
import Description from '../Form/Description';
import useGetSiteSettings from '../../models/useGetSiteSettings';
import MultiSelect from '../MultiSelect';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';

export default function LocationFilterText({
    onChange,
    onClearFilter,
}
) {
    const { data } = useGetSiteSettings();
    console.log("+++++++++++++++++data", data);
    const locationNameOptions = data?.location;
    const locationIDOptions = data?.location;
    const countries = data?.country.map(data => {
        return {
            value: data,
            label: data
        }
    }) || [];

    return (
        <div className="mt-3">
            <h3><FormattedMessage id="FILTER_LOCATION" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_DESC" />
            </Description>
            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LOCATION_NAME"
                options={locationNameOptions}
                onChange={onChange}
                term="terms"
                field="name"

            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LOCATION_ID"
                options={locationIDOptions}
                onChange={onChange}
                term="terms"
                field="id"

            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_COUNTRY"
                options={countries}
                onChange={onChange}
                term="terms"
                field="country"

            />

        </div>
    );
}