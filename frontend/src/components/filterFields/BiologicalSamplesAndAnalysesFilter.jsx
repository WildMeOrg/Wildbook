
import React, { useState } from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import Description from '../Form/Description';
import FormGroupText from '../Form/FormGroupText';
import FormMeasurements from '../Form/FormMeasurements';
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';


export default function BiologicalSamplesAndAnalysesFilter({
    onChange,
    data
}) {
    const label = <FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE" />
    const [isChecked, setIsChecked] = React.useState(false);
    const bioChemicalOptions = Object.keys(data?.bioMeasurement || {}) || [];
    const microSatelliteMarkerLoci = [
        {
            "analysisId": "ZAMBONI",
            "loci": {
                "X": [
                    1,
                    2
                ],
                "Y": [
                    3,
                    4
                ],
                "Z": [
                    55,
                    66
                ]
            }
        }
    ];
    const [checkedState, setCheckedState] = useState({});
    const [alleleLength, setAlleleLength] = React.useState(false);
    const [length, setLength] = React.useState(null);

    const [bioChemicalValue, setBioChemicalValue] = React.useState(null);
    console.log("bioChemicalOptions", bioChemicalOptions);
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
                    checked={isChecked}
                    onChange={() => {
                        setIsChecked(!isChecked);
                        // onChange({
                        //     filterId: "biologicalSampleId",
                        //     clause: "filter",
                        //     query: {
                        //         "exists": {
                        //             "field": "biologicalSampleId"
                        //         }
                        //     }

                        // })
                        onChange({
                            filterId: "biologicalSampleId",
                            clause: "filter",
                            query: {
                                "term": {
                                    "biologicalSampleId": isChecked
                                }
                            }
                        })
                    }
                    }
                />
            </Form>
            <FormGroupText
                label="FILTER_BIOLOGICAL_SAMPLE_ID_CONTAINS"
                onChange={onChange}
                field="tissueSampleIds"
                term="match"
                filterId={"tissueSampleIds"}
            />
            <FormMeasurements
                onChange={onChange}
                data={bioChemicalOptions || []}
                field={"biologicalSampleIsotopeValues"}
                filterId={"biologicalSampleIsotopeValues"}
            />
            <div className='d-flex flex-row justify-content-between'>
                <h5><FormattedMessage id="FILTER_MARKER_LOCI" /></h5>
                <div className="d-flex flex-row">
                    <Form.Check
                        label={<FormattedMessage id="FILTER_RELAX_ALLELE_LENGTH" />}
                        type="checkbox"
                        id="custom-checkbox_ALLELE_LENGTH"
                        checked={alleleLength}
                        onChange={() => {
                            setAlleleLength(!alleleLength);
                        }
                        }
                    />
                    <Form.Control
                        style={{
                            width: "60px"
                        }}
                        type="number"
                        onChange={(e) => {
                            setLength(e.target.value);
                        }}
                        value={length}

                    /></div>

            </div>


            {
                microSatelliteMarkerLoci?.map((data) => {


                    const handleCheckboxChange = (item) => {
                        setCheckedState(prevState => ({
                            ...prevState,
                            [item]: !prevState[item]
                        }));
                    };

                    const inputs = Object.keys(data.loci).map((item) => {
                        console.log("item", item);
                        return <FormGroup className="d-flex flex-column gap-2">
                            <Form.Check
                                type="checkbox"
                                id="custom-checkbox"
                                label={item}
                                checked={checkedState[item] || false}
                                onChange={() => {
                                    handleCheckboxChange(item);
                                }
                                }
                            />
                            <div className="d-flex flex-row gap-3 ms-5">
                                <div className="d-flex flex-column w-50">
                                    <FormLabel ><FormattedMessage id={"ALLELE1"} defaultMessage={"ALLELE1"} /></FormLabel>
                                    <FormControl className="mr-2"
                                        type="text"
                                        placeholder="Type Here"
                                        onChange={(e) => {
                                            if (checkedState[item]) {
                                                if (alleleLength) {
                                                    onChange({
                                                        filterId: `${item}.allele1`,
                                                        clause: "filter",
                                                        query: {
                                                            "range": {
                                                                [item]: {
                                                                    "gte": parseInt(e.target.value, 10) - parseInt(length),
                                                                    "lte": parseInt(e.target.value, 10) + parseInt(length)
                                                                }
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    onChange({
                                                        filterId: `${item}.allele1`,
                                                        clause: "filter",
                                                        query: {
                                                            "match": {
                                                                [item]: e.target.value
                                                            }
                                                        }
                                                    });
                                                }

                                            }
                                        }}
                                    />
                                </div>
                                <div className="d-flex flex-column w-50">
                                    <FormLabel><FormattedMessage id={"ALLELE1"} defaultMessage={"ALLELE2"} /></FormLabel>
                                    <FormControl
                                        type="text"
                                        placeholder="Type Here"
                                        onChange={(e) => {
                                            if (checkedState[item]) {
                                                if (alleleLength) {
                                                    onChange({
                                                        filterId: `${item}.allele2`,
                                                        clause: "filter",
                                                        query: {
                                                            "range": {
                                                                [item]: {
                                                                    "gte": parseInt(e.target.value, 10) - parseInt(length),
                                                                    "lte": parseInt(e.target.value, 10) + parseInt(length)
                                                                }
                                                            }
                                                        }
                                                    });
                                                } else {
                                                    onChange({
                                                        filterId: `${item}.allele2`,
                                                        clause: "filter",
                                                        query: {
                                                            "match": {
                                                                [item]: e.target.value
                                                            }
                                                        }
                                                    });
                                                }

                                            }
                                        }}
                                    />
                                </div>
                            </div>

                        </FormGroup>
                    })

                    return inputs;

                })}


        </div>
    );
}