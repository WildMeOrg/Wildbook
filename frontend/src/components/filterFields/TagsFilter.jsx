import React from "react";
import FormDualInput from "../Form/FormDualInput";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";


export default function TagsFilter() {
  const metalTagLocations = [
    "Metal Tag Location 1",
    "Metal Tag Location 2",
    "Metal Tag Location 3",
    "Metal Tag Location 4",
    "Metal Tag Location 5",
  ];
  return (
    <div>
      <h3><FormattedMessage id="FILTER_IDENTITY" /></h3>
      <Description>
        <FormattedMessage id="FILTER_IDENTITY_DESC" />
      </Description>
      <h5><FormattedMessage id="FILTER_METAL_TAGS" /></h5>
      {metalTagLocations.map((location) => {
        return (
          <FormGroupText
            noDesc={true}
            label={location}
          />
        );
      })}
      <h5><FormattedMessage id="FILTER_ACOUSTIC_TAGS" /></h5>
      <FormDualInput
        label1="FILTER_SERIAL_NUMBER"
        label2="FILTER_ACOUSTIC_TAG_ID"
        width="50"
      />
      <h5><FormattedMessage id="FILTER_SATELLITE_TAGS" /></h5>
      <FormGroupText
        noDesc={true}
        label="NAME"
      />
      <FormGroupText
        noDesc={true}
        label="SERIAL_NUMBER"
      />
      <FormGroupText
        noDesc={true}
        label="ARGOS_PPT_NUMBER"
      />
    </div>
  );
}