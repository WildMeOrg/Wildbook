import React from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import FormGroupMultiSelect from './FormGroupMultiSelect';
import FormGroupText from './FormGroupText';
import Description from './Description';
import Select from 'react-select';
import MultiSelect from '../MultiSelect';

const FormMeasurements = ({ label1="", label2="", width="50" }) => {
    const width2 = width ? 100 - width : 50;

    const options = [
        { value: 'gte', label: '>=' },
        { value: 'equals', label: '=' },
        { value: 'lte', label: '<=' }
    ]
    return (
        <div className="d-flex flex-row justify-content-between gap-2 mt-1">
            <div style={{
                    width: `${width}%`,
                }}
            >
                <FormGroupMultiSelect
                    isMulti={false}
                    options={options}
                />
                
            </div>
            
            <div
                style={{
                    width: `${width2}%`
                }}
            >
                <FormGroupText 
                    noLabel={true}
                />
            </div>
        </div>
    );

}

export default FormMeasurements;