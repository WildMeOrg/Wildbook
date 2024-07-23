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
    const countries = ["china", "canada"].map((item) => {
        return {
            value: item,
            label: item
        };
    }   
    ) || [];
    // data?.country.map(data => {
    //     return {
    //         value: data,
    //         label: data
    //     }
    // }) || [];

    return (
        <div className="mt-3">
            <h3><FormattedMessage id="FILTER_LOCATION" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_DESC" />
            </Description>
            <FormGroupText
                label="FILTER_VERBATIM_LOCATION"
                onChange={onChange}
                term="match"
                field="verbatimLocality"
                filterId={"Verbatim Location"}
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