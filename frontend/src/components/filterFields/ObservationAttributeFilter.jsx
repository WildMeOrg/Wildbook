import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import BrutalismButton from "../BrutalismButton";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";
import DynamicInputs from "../Form/DynamicInputs";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";

export default function ObservationAttributeFilter(
    {
        onChange,
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
    }) || [];
    const genusAndSpeciesOptions = data?.siteTaxonomies?.map(item => {
        return {
            value: item?.scientificName,
            label: item?.scientificName
        };
    }) || [];
    const behaviourOptions = data?.behavior?.map(item => {
        return {
            value: item,
            label: item
        };
    }) || [];
    const patternCodeOptions = data?.patterningCode?.map(item => {
        return {
            value: item,
            label: item
        };
    }) || [];
    const measurementsOptions = data?.measurement || [];

    return (
        <div style={{
            overflow: "visible",
        }}>
            <h3><FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE" /></h3>
            <Description>
                <FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE_DESC" />
            </Description>

            <FormGroupMultiSelect
                isMulti={true}
                noDesc={true}
                label="FILTER_SEX"
                options={sexOptions}
                onChange={onChange}
                field="sex"
                term="terms"
                filterKey="Sex"
            />
            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_LIFE_STATUS"
                noDesc={true}
                options={livingStatusOptions}
                onChange={onChange}
                field="livingStatus"
                term="terms"
                filterKey="Life Status"
            />

            <FormGroupMultiSelect
                isMulti={true}
                label="FILTER_GENUS_AND_SPECIES"
                noDesc={true}
                options={genusAndSpeciesOptions}
                onChange={onChange}
                field="taxonomy"
                term="terms"
                filterId={"Taxonomy"}
                filterKey={"Genus and Species"}
            />

            <FormGroupText
                label="FILTER_SIGHTING_ID"
                noDesc={true}
                onChange={onChange}
                term="match"
                field="occurrenceId"
                filterId={"occurrenceId"}
                filterKey={"Sighting ID"}
            />

            <FormGroup className="mt-2">
                <FormLabel><FormattedMessage id="FILTER_OBSERVATION_SEARCH" /></FormLabel>
                
                <DynamicInputs
                    onChange={onChange}
                />
            </FormGroup>
            <FormGroupText
                label="FILTER_OBSERVATION_COMMENTS"
                onChange={onChange}
                term="match"
                field="occurrenceRemarks"
                filterId={"occurrenceRemarks"}
                filterKey={"Observation Comments"}
            />

            <FormGroupMultiSelect
                isMulti={true}
                noDesc={true}
                label="FILTER_BEHAVIOUR"
                onChange={onChange}
                options={behaviourOptions}
                field="behavior"
                term="terms"
                filterKey="Behavior"
            />

            <FormGroupMultiSelect
                isMulti={true}
                noDesc={true}
                label="FILTER_PATTERNING_CODE"
                onChange={onChange}
                options={patternCodeOptions}
                field={"patterningCode"}
                term={"terms"}
                filterKey={"Patterning Code"}
            />

            <FormGroup className="mt-2">
                <FormLabel><FormattedMessage id="FILTER_MEASUREMENTS" /></FormLabel>
                <FormMeasurements
                    data={measurementsOptions}
                    onChange={onChange}
                    filterId={"measurements"}
                    field={"measurements"}
                />
            </FormGroup>

        </div>
    );
}