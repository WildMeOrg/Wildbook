import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";

export default function SightingsObservationAttributeFilter({ store }) {
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
        term="match"
        field="occurrenceId"
        filterId={"occurrenceId"}
        filterKey={"Sighting ID"}
        store={store}
      />

      <FormGroupText
        label="FILTER_SIGHTING_COMMENTS"
        noDesc={true}
        term="match"
        field="occurrenceComments"
        filterId={"occurrenceComments"}
        filterKey={"Sighting Comments"}
        store={store}
      />

      <FormGroupText
        label="FILTER_VISIBILIY_INDEX"
        noDesc={true}
        term="match"
        field="occurrenceVisibilityIndex"
        filterId={"occurrenceVisibilityIndex"}
        filterKey={"Visibility Index"}
        store={store}
      />

      <FormGroupText
        label="FILTER_OBSERVATION_COMMENTS"
        term="match"
        field="occurrenceRemarks"
        filterId={"occurrenceRemarks"}
        filterKey={"Observation Comments"}
        store={store}
      />
    </div>
  );
}
