import React from 'react';
import { FormattedMessage } from 'react-intl';
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import Description from './Description';

const FormDualInput = ({ label1, label2, width }) => {
    const width2 = width? 100-width : 50;
    return (        
            <div className="d-flex flex-row justify-content-between gap-2 mt-1">
                <div className={`w-${width}`}>
                    <FormLabel><FormattedMessage id={label1} /></FormLabel>
                    <FormControl type="text" placeholder="Type Here" />
                </div>
                <div 
                    style={{
                        width: `${width2}%`
                    }}
                >
                    <FormLabel><FormattedMessage id={label2} /></FormLabel>
                    <FormControl type="text" placeholder="Type Here" />
                </div>
            </div>
    );

}

export default FormDualInput;