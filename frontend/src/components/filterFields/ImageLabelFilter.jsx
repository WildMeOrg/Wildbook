import React, { Fragment, useEffect } from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import MultiSelect from '../MultiSelect';
import FormGroup from 'react-bootstrap/FormGroup';
import Description from '../Form/Description';
import { filter } from 'lodash-es';
import FormGroupText from '../Form/FormGroupText';
import Select from 'react-select';

const colourStyles = {
    option: (styles) => ({
        ...styles,
        color: 'black', 
    }),
    control: (styles) => ({ ...styles, backgroundColor: 'white' }),
    singleValue: (styles) => ({ ...styles, color: 'black' }), 
  };

export default function ImageLabelFilter({
    data,
    onChange,
}) {
    const keywordsOptions = data?.keyword?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const labelledKeywordsOptions = Object.entries(data?.labeledKeyword || []).map(([key, value]) => {
        return {
            value: key,
            label: key
        }
    }) || [];

    const [labelledKeyword, setLabelledKeyword] = React.useState(null)

    const [labelledKeywordsValueOptions, setLabelledKeywordsValueOptions] = React.useState([]);
    useEffect(() => {
        setLabelledKeywordsValueOptions((data?.labeledKeyword[labelledKeyword] || []).map(
            item => {
                return {
                    value: item,
                    label: item
                }
            }
        ))
        const testOptions = (data?.labeledKeyword[labelledKeyword] || []).map(
            item => {
                return {
                    value: item,
                    label: item
                }
            }
        )
    }, [labelledKeyword])

    const viewPointOptions = data?.annotationViewpoint?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const iaClassOptions = data?.iaClass?.map(item => {
        return {
            value: item,
            label: item
        }
    }) || [];

    const label = <FormattedMessage id="HAS_AT_LEAST_ONE_ASSOCIATED_PHOTO_OR_VIDEO" />
    const [isChecked_photo, setIsChecked_photo] = React.useState(false);
    const [isChecked_keyword, setIsChecked_keyword] = React.useState(false);

    const term = isChecked_keyword? "terms" : "match";
    const field = "keywords";
    const filterId = "keywords";

    return (

        <div>
            <h3><FormattedMessage id="FILTER_IMAGE_LABEL" /></h3>
            <Form>
                <Form.Check
                    type="checkbox"
                    id="custom-checkbox"
                    label={label}
                    checked={isChecked_photo}
                    onChange={() => {
                        setIsChecked_photo(!isChecked_photo);
                        onChange({
                            filterId: "numberMediaAssets",
                            clause: "filter",
                            query: {
                                "range": {
                                    "numberMediaAssets": {
                                        "gte": 1                                    
                                    }
                                }
                            },
                        })
                    }}
                />
            </Form>

            <div className="d-flex flex-row justify-content-between">
                <Form.Label>
                    <FormattedMessage id="FILTER_KEYWORDS" />
                </Form.Label>

                <Form.Check
                    type="checkbox"
                    id="custom-checkbox"
                    label={<FormattedMessage id="USE_AND_OPERATOR" />}
                    checked={isChecked_keyword}
                    onChange={() => {
                        setIsChecked_keyword(!isChecked_keyword);
                    }
                    }
                />
            </div>


            <Select
                isMulti={setIsChecked_photo}
                options={keywordsOptions}
                styles={colourStyles}
                onChange={(e) =>
                    onChange({
                        filterId: { filterId },
                        clause: "filter",
                        query: {
                            [term]: {
                                [field]: setIsChecked_photo ? e.map(item => item.value) : e.value
                            }
                        }
                    })
                }
            />
            <FormGroup>
                <Form.Label><FormattedMessage id="FILTER_LABELLED_KEYWORDS" /></Form.Label>
                <Description>
                    <FormattedMessage id={`FILTER_LABELLED_KEYWORDS_DESC`} />
                </Description>
                <div className="d-flex flex-row gap-3">
                    <div className="w-50">
                        <Form.Label><FormattedMessage id="LABEL" /></Form.Label>
                        <Select
                            onChange={(e) => {
                                setLabelledKeyword(e.value)

                            }}
                            options={labelledKeywordsOptions}
                        />
                    </div>
                    <div className="w-50">
                        <Form.Label><FormattedMessage id="VALUE" /></Form.Label>
                        <Select
                            options={labelledKeywordsValueOptions}
                            onChange={(e) => {
                                onChange({
                                    filterId: "labelledKeywords",
                                    clause: "filter",
                                    query: {
                                        "match": {
                                            [labelledKeyword]: e.value
                                        }
                                    }
                                })
                            }}
                        />
                    </div>

                </div>
            </FormGroup>

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_VIEWPOINT"
                options={viewPointOptions}
                filterId="viewpoint"
                term="terms"
                field={"viewpoint"}
                onChange={onChange}
            />

            <FormGroupMultiSelect
                label="FILTER_CLASS"
                options={iaClassOptions}
                filterId="iaClass"
                term="terms"
                onChange={onChange}
            />
        </div>
    );
}