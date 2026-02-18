import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import ContainerWithSpinner from "../ContainerWithSpinner";

export default function ObservationAttributeFilter({ data, store }) {
  const sexOptions =
    data?.sex?.map((item) => {
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
          label="FILTER_INDIVIDUAL_SEX"
          options={sexOptions}
          field="individualSex"
          term="terms"
          filterKey="individualSex"
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
      <ContainerWithSpinner loading={store.siteSettingsLoading}>
        <FormGroupMultiSelect
          isMulti={true}
          label="FILTER_INDIVIDUAL_TAXONOMY"
          noDesc={true}
          options={genusAndSpeciesOptions}
          field="individualTaxonomy"
          term="terms"
          filterId={"individualTaxonomy"}
          filterKey={"Individual Taxonomy"}
          store={store}
          loading={store.siteSettingsLoading}
        />
      </ContainerWithSpinner>
    </div>
  );
}
