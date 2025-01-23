import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import FormGroupText from "../Form/FormGroupText";
import DynamicInputs from "../Form/DynamicInputs";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";

export default function ObservationAttributeFilter({ onChange, data }) {

    const measurementsOptions = data?.measurement || [];

    return (
        <div
            style={{
                overflow: "visible",
            }}
        >
            <h4>
                <FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE" />
            </h4>
            <Description>
                <FormattedMessage id="FILTER_OBSERVATION_ATTRIBUTE_DESC" />
            </Description>

            <FormGroupText
                label="FILTER_SIGHTING_ID"
                noDesc={true}
                onChange={onChange}
                term="match"
                field="occurrenceId"
                filterId={"occurrenceId"}
                filterKey={"Sighting ID"}
            />

            <FormGroupText
                label="FILTER_SIGHTING_COMMENTS"
                noDesc={true}
                onChange={onChange}
                term="match"
                field="sightingcomments"
                filterId={"sightingcomments"}
                filterKey={"Sighting Comments"}
            />

            <FormGroupText
                label="FILTER_VISIBILIY_INDEX"
                noDesc={true}
                onChange={onChange}
                term="match"
                field="visibilityIndex"
                filterId={"visibilityIndex"}
                filterKey={"Visibility Index"}
            />

            <FormGroupText
                label="FILTER_OBSERVATION_COMMENTS"
                onChange={onChange}
                term="match"
                field="occurrenceRemarks"
                filterId={"occurrenceRemarks"}
                filterKey={"Observation Comments"}
            />

            <FormGroup className="mt-2">
                <FormLabel>
                    <FormattedMessage id="FILTER_MEASUREMENTS" />
                </FormLabel>
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
