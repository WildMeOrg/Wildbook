import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import BrutalismButton from "../BrutalismButton";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";
import FormDualInput from "../Form/FormDualInput";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";

export default function ObservationAttributeFilter(
    { onChange }
) {

    const sexOptions = [
        {
            value: 'Australia',
            label: "Australia"
        },
        {
            value: 'Cambodia',
            label: "Cambodia"
        },

    ];
    const lifeStatusOptions = [];
    const genusAndSpeciesOptions = [
        {
            value: 'Equus quagga',
            label: "Equus quagga"
        },
        {
            value: 'Equus quagga',
            label: "Equus quagga"
        },
    ];
    const behaviourOptions = [];
    const patternCodeOptions = [];
    const measurementsOptions = [ "WaterTemperature",
        "Salinity"];

    const options = [
        { value: '1', label: 'Behaviour 1' },
        { value: '2', label: 'Behaviour 2' },
        { value: '3', label: 'Behaviour 3' }
    ];


    return (
        <div>
            <h3><FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE" /></h3>
            <Description>
                <FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE_DESC" />
            </Description>

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_SEX"
                options={sexOptions}
            />
            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LIFE_STATUS"
                options={lifeStatusOptions}
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_GENUS_AND_SPECIES"
                options={genusAndSpeciesOptions}
                onChange={onChange}
              
            />
            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_OBSERVATION_SEARCH" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_OBSERVATION_SEARCH_DESC" />
                </Description>
                <FormDualInput
                    label1="FILTER_OBSERVATION_NAME"
                    label2="FILTER_OBSERVATION_VALUE"
                    width="50"
                />
            </FormGroup>

            <BrutalismButton style={{
                marginTop: '10px'
            }}
                borderColor="#fff"
                color="white"
                backgroundColor="transparent"
            >
                <i className="bi bi-plus-square" style={{ marginRight: "10px" }}></i>
                <FormattedMessage id="FILTER_ADD_OBSERVATION_SEARCH" />
            </BrutalismButton>

            <FormGroupText
                label="FILTER_OBSERVATION_COMMENTS_INCLUDE"
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_DEMONSTRATED_BEHAVIOUR"
                options={behaviourOptions}
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_PATTERNING_CODE"
                options={patternCodeOptions}
            />

            <FormGroup>
                <FormLabel><FormattedMessage id="FILTER_MEASUREMENTS" /></FormLabel>
                <Description>
                    <FormattedMessage id="FILTER_MEASUREMENTS_DESC" />
                </Description>
                

                {
                    measurementsOptions.map((option, index) => {
                        return (
                            <FormMeasurements
                                label1={option}
                                label2={option}
                                key={option}
                                width="30"
                            />
                        );
                    })
                }

            </FormGroup>

        </div>
    );
}