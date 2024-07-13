import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import BrutalismButton from "../BrutalismButton";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";
import FormDualInput from "../Form/FormDualInput";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";

export default function ObservationAttributeFilter(
    { onChange,
      data,
     }
) {
    const sexOptions = data?.sex?.map((item) => {
        return {
            value: item,
            label: item
        };
    }) || [];
    const livingStatusOptions = data?.livingStatus?.map(item => {
        return {
            value: item,
            label: item
        };
    })||[];
    const genusAndSpeciesOptions = data?.siteTaxonomies?.map(item => {
        return {
            value: item?.scientificName,
            label: item?.scientificName
        };
    })||[];
    const behaviourOptions = data?.behavior?.map(item => {
        return {
            value: item,
            label: item
        };
    })||[];
    const patternCodeOptions = data?.patterningCode?.map(item => {
        return {
            value: item,
            label: item
        };
    })||[];
    const measurementsOptions = data?.measurement || [];

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
                onChange={onChange}
                field="sex"
                term="terms"
            />
            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LIFE_STATUS"
                options={livingStatusOptions}
                onChange={onChange}
                field="livingStatus"
                term="terms"
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_GENUS_AND_SPECIES"
                options={genusAndSpeciesOptions}
                onChange={onChange}
                field="taxonomy"
                term="terms"
              
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
                onChange={onChange}
                options={behaviourOptions}
                field="behaviour"
                term="terms"
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_PATTERNING_CODE"
                onChange={onChange}
                options={patternCodeOptions}
                field={"patternCode"}
                term={"terms"}
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