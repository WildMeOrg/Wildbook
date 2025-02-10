import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import DynamicInputs from "../Form/DynamicInputs";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";

export default function ObservationAttributeFilter({ data, store }) {
  const sexOptions =
    data?.sex?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
  const livingStatusOptions =
    data?.livingStatus?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
  const lifeStageOptions =
    data?.lifeStage?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
  const genusAndSpeciesOptions =
    data?.siteTaxonomies?.map((item) => {
      return {
        value: item?.scientificName,
        label: item?.scientificName,
      };
    }) || [];
  const behaviourOptions =
    data?.behavior?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
  const patternCodeOptions =
    data?.patterningCode?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
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

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_SEX"
        options={sexOptions}
        field="sex"
        term="terms"
        filterKey="Sex"
        store={store}
      />
      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_LIFE_STAGE"
        noDesc={true}
        options={lifeStageOptions}
        field="lifeStage"
        term="terms"
        filterKey="Life Stage"
        store={store}
      />
      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_LIVING_STATUS"
        noDesc={true}
        options={livingStatusOptions}
        field="livingStatus"
        term="terms"
        filterKey="Living Status"
        store={store}
      />

      <FormGroupMultiSelect
        isMulti={true}
        label="FILTER_GENUS_AND_SPECIES"
        noDesc={true}
        options={genusAndSpeciesOptions}
        field="taxonomy"
        term="terms"
        filterId={"Taxonomy"}
        filterKey={"Genus and Species"}
        store={store}
      />

      <FormGroup className="mt-2">
        <FormLabel>
          <FormattedMessage id="FILTER_OBSERVATION_SEARCH" />
        </FormLabel>

        <DynamicInputs store={store}/>
      </FormGroup>

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_BEHAVIOUR"
        options={behaviourOptions}
        field="behavior"
        term="terms"
        filterKey="Behavior"
        store={store}
      />

      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_PATTERNING_CODE"
        options={patternCodeOptions}
        field={"patterningCode"}
        term={"terms"}
        filterKey={"Patterning Code"}
        store={store}
      />

      <FormGroup className="mt-2">
        <FormLabel>
          <FormattedMessage id="FILTER_MEASUREMENTS" />
        </FormLabel>
        <FormMeasurements
          data={measurementsOptions}
          filterId={"measurements"}
          field={"measurements"}
          store={store}
        />
      </FormGroup>
    </div>
  );
}
