
import React from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import Description from '../Form/Description';
import FormGroupText from '../Form/FormGroupText';

export default function BiologicalSamplesAndAnalysesFilter() {
    const label = <FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE" />
    return (
        <div>
            <h3><FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE" /></h3>
            <Description>
                <FormattedMessage id="FILTER_IDENTITY_DESC" />
            </Description>
            <Form>
                <Form.Check
                    type="checkbox"
                    id="custom-checkbox"
                    label={label}
                // checked={isChecked}
                // onChange={handleOnChange}
                />
            </Form>
            <FormGroupText
                label="FILTER_BIOLOGICAL_SAMPLE_ID_CONTAINS"
            />
        </div>
    );
}