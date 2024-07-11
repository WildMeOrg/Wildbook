import React, { Fragment } from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import MultiSelect from '../MultiSelect';
import FormGroup from 'react-bootstrap/FormGroup';
import Description from '../Form/Description';

export default function ImageLabelFilter() {
    const label = <FormattedMessage id="HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO" />
    return (
        <div>
            <h3><FormattedMessage id="FILTER_IMAGE_LABEL" /></h3>
            <Form>
                <Form.Check
                    type="checkbox"
                    id="custom-checkbox"
                    label={label}
                // checked={isChecked}
                // onChange={handleOnChange}
                />
            </Form>
            <FormGroupMultiSelect
                label="FILTER_KEYWORDS"
                options={[]}
            />
            <FormGroup>
                <Form.Label><FormattedMessage id="FILTER_LABELLED_KEYWORDS" /></Form.Label>
                <Description>
                    <FormattedMessage id={`FILTER_LABELLED_KEYWORDS_DESC`} />
                </Description>
                <div className="d-flex flex-row gap-3">
                    <div className="w-50">
                        <Form.Label><FormattedMessage id="LABEL" /></Form.Label>
                        <MultiSelect
                            options={[
                                { value: '1', label: 'Behaviour 1' },
                                { value: '2', label: 'Behaviour 2' },
                                { value: '3', label: 'Behaviour 3' }
                            ]}
                        />
                    </div>
                    <div className="w-50">
                        <Form.Label><FormattedMessage id="VALUE" /></Form.Label>
                        <MultiSelect
                            isMulti={true}
                            options={[
                                { value: '1', label: 'Behaviour 1' },
                                { value: '2', label: 'Behaviour 2' },
                                { value: '3', label: 'Behaviour 3' }
                            ]}
                        />
                    </div>

                </div>
            </FormGroup>

            <FormGroupMultiSelect
                label="FILTER_VIEWPOINT"
                options={[
                    { value: '1', label: 'Behaviour 1' },
                    { value: '2', label: 'Behaviour 2' },
                    { value: '3', label: 'Behaviour 3' }
                ]}
            />

            <FormGroupMultiSelect
                label="FILTER_CLASS"
                options={[
                    { value: '1', label: 'Behaviour 1' },
                    { value: '2', label: 'Behaviour 2' },
                    { value: '3', label: 'Behaviour 3' }
                ]}
            />
        </div>
    );
}