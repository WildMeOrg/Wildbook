import React from "react";
import { FormattedMessage } from "react-intl";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";

export default function IndividualEstimateFilter({ store }) {
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
        field={"occurrenceBestGroupSizeEstimate"}
        term={"match"}
        filterId={"occurrenceBestGroupSizeEstimate"}
        filterKey={"Best Estimate Individuals"}
        store={store}
      />
      <FormGroupText
        label="FILTER_MINIMUM_ESTIMATE_INDIVIDUALS"
        noDesc={true}
        field={"occurrenceMinGroupSizeEstimate"}
        term={"match"}
        filterId={"occurrenceMinGroupSizeEstimate"}
        filterKey={"Minimum Estimate Individuals"}
        store={store}
      />
      <FormGroupText
        label="FILTER_MAXIMUM_ESTIMATE_INDIVIDUALS"
        noDesc={true}
        field={"occurrenceMaxGroupSizeEstimate"}
        term={"match"}
        filterId={"occurrenceMaxGroupSizeEstimate"}
        filterKey={"Maximum Estimate Individuals"}
        store={store}
      />
    </div>
  );
}
