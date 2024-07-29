
import React, { useState, useEffect } from 'react';
import { FormattedMessage } from 'react-intl';
import Form from 'react-bootstrap/Form';
import Description from '../Form/Description';
import FormGroupText from '../Form/FormGroupText';
import FormMeasurements from '../Form/FormMeasurements';
import { FormGroup, FormLabel, FormControl } from 'react-bootstrap';
import FormGroupMultiSelect from '../Form/FormGroupMultiSelect';
import BioMeasurements from '../Form/BioMeasurements';
import { useIntl } from 'react-intl';

export default function BiologicalSamplesAndAnalysesFilter({
    onChange,
    data
}) {

    const intl = useIntl();
    const label = <FormattedMessage id="FILTER_HAS_BIOLOGICAL_SAMPLE" />
    const [isChecked, setIsChecked] = React.useState(false);
    const bioMeasurementOptions = Object.entries(data?.bioMeasurement || {}).map(
        item => item[0]
    ) || [];

    const microSatelliteMarkerLoci = data?.loci || [];

    const [checkedState, setCheckedState] = useState({});
    const [alleleLength, setAlleleLength] = React.useState(false);
    const [length, setLength] = React.useState(null);

    const haploTypeOptions = data?.haplotype.map(item => {
        return {
            value: typeof item === "object" ? item.value : item,
            label: typeof item === "object" ? item.label : item
        }
    }) || [];

    const geneticSexOptions = data?.geneticSex.map(item => {
        return {
            value: typeof item === "object" ? item.value : item,
            label: typeof item === "object" ? item.label : item
        }
    }) || [];

    const [currentValues, setCurrentValues] = useState({});


    const buildQuery_range = (data, i, value) => {
        onChange({
            filterId: `microsatelliteMarkers.loci.${data}.allele${i}`,
            clause: "filter",
            query: {
                "range": {
                    [`microsatelliteMarkers.loci.${data}.allele${i}`]: {
                        "gte": parseInt(value, 10) - parseInt(length),
                        "lte": parseInt(value, 10) + parseInt(length)
                    }
                }
            }
        });
    }
    const buildQuery_match = (data, i, value) => {
        onChange({
            filterId: `microsatelliteMarkers.loci.${data}.allele${i}`,
            clause: "filter",
            query: {
                "match": {
                    [`microsatelliteMarkers.loci.${data}.allele${i}`]: value
                }
            }
        });
    }

    useEffect(() => {
        // Iterate through each marker and re-calculate the query if necessary        
        Object.keys(currentValues).forEach(data => {
            if (checkedState[data]) {
                const value0 = currentValues[data]?.allele0;
                const value1 = currentValues[data]?.allele1;
                if (value0 !== undefined) {
                    if (alleleLength && length) {
                        buildQuery_range(data, 0, value0);
                    } else {
                        buildQuery_match(data, 0, value0);
                    }
                }
                if (value1 !== undefined) {
                    if (alleleLength && length) {
                        buildQuery_range(data, 1, value1);
                    } else {
                        buildQuery_match(data, 1, value1);
                    }
                }
            }
        });

    }, [alleleLength, length]);

    const handleInputChange = (data, index, value) => {
        setCurrentValues(prevState => ({
            ...prevState,
            [data]: {
                ...prevState[data],
                [`allele${index}`]: value
            }
        }));

        if (checkedState[data]) {
            if (value === "") {
                onChange(null, `microsatelliteMarkers.loci.${data}.allele${index}`);
            } else if (alleleLength && length) {
                buildQuery_range(data, index, value);
            } else {
                buildQuery_match(data, index, value);
            }
        }
    }


    const [bioChemicalValue, setBioChemicalValue] = React.useState(null);
    return (
        <div>
            <h3><FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE" /></h3>
            <Description>
                <FormattedMessage id="FILTER_BIOLOGICAL_SAMPLE_DESC" />
            </Description>
            <Form>
                <Form.Check
                    type="checkbox"
                    id="custom-checkbox"
                    label={label}
                    checked={isChecked}
                    onChange={(e) => {
                        setIsChecked(!isChecked);
                        if (!e.target.checked) {
                            onChange(null, `biologicalSampleId`);
                            return;
                        } else {
                            onChange({
                                filterId: `biologicalSampleId`,
                                clause: "filter",
                                query: {
                                    "exists": {
                                        "field": "tissueSampleIds"
                                    }
                                }
                            })
                        }
                    }
                    }
                />
            </Form>
            <FormGroupText
                label="FILTER_BIOLOGICAL_SAMPLE_ID"
                noDesc
                onChange={onChange}
                field="tissueSampleIds"
                term="match"
                filterId={"tissueSampleIds"}
            />
            <FormGroupMultiSelect
                isMulti={true}
                noDesc={true}
                label="FILTER_HAPLO_TYPE"
                onChange={onChange}
                options={haploTypeOptions || []}
                field={"haplotype"}
                filterId={"haplotype"}
                term={"terms"}
            />

            <FormGroupMultiSelect
                isMulti={true}
                noDesc={true}
                label="FILTER_GENETIC_SEX"
                onChange={onChange}
                options={geneticSexOptions || []}
                field={"geneticSex"}
                term={"terms"}
                filterId={"geneticSex"}
            />

            <BioMeasurements
                data={bioMeasurementOptions}
                onChange={onChange}
                filterId={"biologicalMeasurements"}
                field={"biologicalMeasurements"}
            />

            <div className='d-flex flex-row justify-content-between'>
                <h5><FormattedMessage id="FILTER_MARKER_LOCI" /></h5>
                <div className="d-flex flex-row align-items-center">
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
                    <FormControl
                        type="number"
                        disabled={!alleleLength}
                        placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
                        style={{
                            width: "70px",
                            marginLeft: "10px",
                        }}
                        onChange={(e) => {
                            setLength(e.target.value);                            
                        }}
                    />
                </div>
            </div>
            {
                microSatelliteMarkerLoci?.map((data) => {
                    const handleCheckboxChange = (item) => {
                        setCheckedState(prevState => ({
                            ...prevState,
                            [item]: !prevState[item]
                        }));
                    };

                    return <FormGroup className="d-flex flex-column gap-2">
                        <Form.Check
                            type="checkbox"
                            id="custom-checkbox"
                            label={data}
                            checked={checkedState[data] || false}
                            onChange={() => {
                                handleCheckboxChange(data);
                                if (checkedState[data]) {
                                    onChange(null, `microsatelliteMarkers.loci.${data}.allele0`);
                                    onChange(null, `microsatelliteMarkers.loci.${data}.allele1`);
                                } else {
                                    if (currentValues[data]?.allele0) {
                                        if(alleleLength && length){
                                            buildQuery_range(data, 0, currentValues[data]?.allele0);
                                        }
                                        else{
                                            buildQuery_match(data, 0, currentValues[data]?.allele0);
                                        }
                                    }
                                    if (currentValues[data]?.allele1) {
                                        if(alleleLength && length){
                                            buildQuery_range(data, 1, currentValues[data]?.allele1);
                                        }else {
                                        buildQuery_match(data, 1, currentValues[data]?.allele1);
                                        }
                                    }

                            }
                            }}
                        />
                        <div className="d-flex flex-row gap-3 ms-5">
                            <div className="d-flex flex-column w-50">
                                <FormLabel ><FormattedMessage id={"FILTER_ALLELE1"} defaultMessage={"Allele1"} /></FormLabel>
                                <FormControl className="mr-2"
                                    type="text"
                                    placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
                                    onChange={(e) => handleInputChange(data, 0, e.target.value)}
                                />
                            </div>
                            <div className="d-flex flex-column w-50">
                                <FormLabel><FormattedMessage id={"FILTER_ALLELE2"} defaultMessage={"Allele2"} /></FormLabel>
                                <FormControl
                                    type="text"
                                    placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
                                    onChange={(e) => handleInputChange(data, 1, e.target.value)}
                                />
                            </div>
                        </div>

                    </FormGroup>
                })
            }
        </div>
    );
}