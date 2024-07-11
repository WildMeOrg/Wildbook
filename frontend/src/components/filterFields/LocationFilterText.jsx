import React, { useState } from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl, FormSelect } from 'react-bootstrap';
import Description from '../Form/Description';
import useGetSiteSettings from '../../models/useGetSiteSettings';
import MultiSelect from '../MultiSelect';
import { set } from 'lodash-es';

export default function LocationFilterText({
    formFilters,
    setFormFilters,
    test,
    onChange,
    onClearFilter,
}
) {
    const { data } = useGetSiteSettings();
    const locations = data?.locationID || [{ value: 'VB', label: 'Country 1' },
    { value: 'KB', label: 'Country 2' },
    { value: 'KOB', label: 'Country   3' }];
    const countries = data?.country || [
        { value: '1', label: 'Country 1', realValue: 'VB'},
        { value: '2', label: 'Country 2', realValue: 'KB' },
        { value: '3', label: 'Country   3', realValue: 'KOB' }
    ];


    return (
        <div className="mt-3">
            <h3><FormattedMessage id="FILTER_LOCATION" /></h3>
            <Description>
                <FormattedMessage id="FILTER_LOCATION_DESC" />
            </Description>

            <FormGroup style={{
                marginRight: '10px',
            }}>
                <FormLabel><FormattedMessage id="FILTER_LOCATION_NAME" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_LOCATION_NAME_DESC" />
                </Description>
                <FormControl
                    type="text"
                    placeholder={"Type Here"}
                    onChange={(e) => 
                          onChange({                           
                                
                                    "match": {
                                      ["locationName"]: e.target.value     
                                    }
                                          
                          })
                    }
                   
                />
            </FormGroup>
            <FormGroup style={{ marginRight: '10px' }}>
                <FormLabel>
                    <FormattedMessage id="FILTER_LOCATION_ID" defaultMessage="Location ID" />
                </FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_LOCATION_ID_DESC" defaultMessage="Enter the location ID." />
                </Description>

                <MultiSelect
                    isMulti={true}
                    options={locations}
                    onChange={(e) => 
                        onChange({                           
                              
                                  "terms": {
                                    ["locationId"]: e.map((item) => item.value)    
                                  }
                                        
                        })
                  }
                />

            </FormGroup>

            <FormGroup style={{ marginRight: '10px' }}>
                <FormLabel>
                    <FormattedMessage id="FILTER_COUNTRY" defaultMessage="Country" />
                </FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_COUNTRY_DESC" defaultMessage="Enter the COUNTRY" />
                </Description>

                <MultiSelect
                    isMulti={true}
                    options={countries}
                    onChange={(e) => 
                        onChange({                           
                              
                                  "terms": {
                                    ["country"]: e.map((item) => item.value)    
                                  }
                                        
                        })
                  }
                />
            </FormGroup>
        </div>
    );
}