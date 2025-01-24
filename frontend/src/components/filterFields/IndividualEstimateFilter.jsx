import React from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";

export default function IndividualEstimateFilter({ onChange }) {
  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_INDIVIDUAL_ESTIMATE" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_INDIVIDUAL_ESTIMATE_DESC" />
      </Description>

      <FormGroupText
        label="FILTER_BEST_ESTIMATE_INDIVIDUALS"
        noDesc={true}
        field={"bestEstimateIndividuals"}
        term={"match"}
        filterId={"bestEstimateIndividuals"}
        onChange={onChange}
        filterKey={"Best Estimate Individuals"}
      />
      <FormGroupText
        label="FILTER_MINIMUM_ESTIMATE_INDIVIDUALS"
        noDesc={true}
        field={"minimumEstimateIndividuals"}
        term={"match"}
        filterId={"minimumEstimateIndividuals"}
        onChange={onChange}
        filterKey={"Minimum Estimate Individuals"}
      />
      <FormGroupText
        label="FILTER_MAXIMUM_ESTIMATE_INDIVIDUALS"
        noDesc={true}
        field={"groupComposition"}
        term={"match"}
        filterId={"groupComposition"}
        onChange={onChange}
        filterKey={"Group Composition"}
      />
    </div>
  );
}
