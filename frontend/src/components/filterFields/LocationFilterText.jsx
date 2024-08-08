import React, { useState } from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl, FormSelect } from 'react-bootstrap';
import Description from '../Form/Description';
import useGetSiteSettings from '../../models/useGetSiteSettings';
import MultiSelect from '../MultiSelect';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import FormGroupText from '../Form/FormGroupText';

export default function LocationFilterText({
    onChange,
}
) {
    const { data } = useGetSiteSettings();
    const countries = data?.country?.map(data => {
        return {
            value: data,
            label: data
        }
    }) || [];

    return (
        <div className="mt-3">
            <FormGroupText
                label="FILTER_VERBATIM_LOCATION"
                onChange={onChange}
                term="match"
                field="verbatimLocality"
                filterId={"verbatimLocality"}
                filterKey = "Verbatim Location"
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_COUNTRY"
                options={countries}
                onChange={onChange}
                term="terms"
                field="country"
                filterKey="Country"
            />

        </div>
    );
}