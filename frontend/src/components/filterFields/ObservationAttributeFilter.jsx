import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import DynamicInputs from "../Form/DynamicInputs";
import { FormLabel, FormGroup } from "react-bootstrap";
import FormMeasurements from "../Form/FormMeasurements";
import FormGroupText from "../Form/FormGroupText";
import ContainerWithSpinner from "../ContainerWithSpinner";

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
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_SEX"
          options={sexOptions}
          field="sex"
          term="terms"
          filterKey="Sex"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupText
          label="FILTER_NOTICEABLE_SCARRING"
          noDesc={true}
          term="match"
          field="distinguishingScar"
          filterId={"distinguishingScar"}
          filterKey={"Noticeable Scarring"}
          store={store}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          label="FILTER_LIFE_STAGE"
          noDesc={true}
          options={lifeStageOptions}
          field="lifeStage"
          term="terms"
          filterKey="Life Stage"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          label="FILTER_LIVING_STATUS"
          noDesc={true}
          options={livingStatusOptions}
          field="livingStatus"
          term="terms"
          filterKey="Living Status"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
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
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <FormGroup className="mt-2">
        <FormLabel>
          <FormattedMessage id="FILTER_OBSERVATION_SEARCH" />
        </FormLabel>

        <DynamicInputs store={store} />
      </FormGroup>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          noDesc={true}
          label="FILTER_BEHAVIOUR"
          options={behaviourOptions}
          field="behavior"
          term="terms"
          filterKey="Behavior"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner
        loading={store.siteSettingsLoading}
      ></ContainerWithSpinner>
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_PATTERNING_CODE"
        options={patternCodeOptions}
        field={"patterningCode"}
        term={"terms"}
        filterKey={"Patterning Code"}
        store={store}
        loading={store.siteSettingsLoading}
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
