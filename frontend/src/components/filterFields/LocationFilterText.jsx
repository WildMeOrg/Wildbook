import React from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl, } from 'react-bootstrap';
import Description from '../Description';
import useGetSiteSettings from '../../models/useGetSiteSettings';

export default function LocationFilterText() {

    const {data} = useGetSiteSettings();    
    const locations = data?.locationID || [];
    const countries = data?.country || [];

    return (
        <div className="mt-3">
            <h3><FormattedMessage id="LOCATION" /></h3>
            <Description>
                <FormattedMessage id="LOCATION_DESC" />
            </Description>

            <FormGroup style={{
                marginRight: '10px',
            }}>
                <FormLabel><FormattedMessage id="LOCATION_NAME" /></FormLabel>
                <Description>
                    <FormattedMessage id="LOCATION_NAME_DESC" />
                </Description>
                <FormControl
                    type="text"
                    placeholder={"Type Here"}
                />
            </FormGroup>
            <FormGroup style={{ marginRight: '10px' }}>
                <FormLabel>
                    <FormattedMessage id="LOCATION_ID" defaultMessage="Location ID" />
                </FormLabel>
                <Description>
                    <FormattedMessage id="LOCATION_ID_DESC" defaultMessage="Enter the location ID." />
                </Description>

                <FormControl as="select" placeholder="Type Here">
                    {locations.map((location) => (
                        <option key={location.id} value={location.id}>
                            {location.name}
                        </option>
                    ))}
                </FormControl>
                
            </FormGroup>

            <FormGroup style={{ marginRight: '10px' }}>
                <FormLabel>
                    <FormattedMessage id="COUNTRY" defaultMessage="Country" />
                </FormLabel>
                <Description>
                    <FormattedMessage id="COUNTRY_DESC" defaultMessage="Enter the COUNTRY" />
                </Description>

                <FormControl as="select" placeholder="Type Here">
                    {countries.map((location) => (
                        <option key={location.id} value={location.id}>
                            {location.name}
                        </option>
                    ))}
                </FormControl>
                
            </FormGroup>
        </div>
    );
}