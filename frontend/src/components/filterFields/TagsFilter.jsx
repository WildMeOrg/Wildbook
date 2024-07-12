import React from "react";
import FormDualInput from "../Form/FormDualInput";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";


export default function TagsFilter({
  data, 
  onChange
}) {
  const metalTagLocations = data?.metalTagLocation?.map((item) => {
    return {
      value: item,
      label: item
    };
  }) || [];
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
            filterId={location}
            field={location}
            term="match"
            onChange={onChange}
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