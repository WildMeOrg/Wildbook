import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";
import { Form, Row, Col } from "react-bootstrap";
import FormDualInputs from "../Form/FormDualInputs";


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
            label={location.item}
            filterId={`$metalTagLocation.{location.item}`}
            field={`$metalTagLocation.{location.item}`}
            term="match"
            onChange={onChange}
            
          />
        );
      })}
      <h5><FormattedMessage id="FILTER_ACOUSTIC_TAGS" /></h5>
      <FormDualInputs 
        label="acousticTags"
        label1="SERIAL_NUMBER"
        label2="ID"
        onChange={onChange}
      />
      <h5><FormattedMessage id="FILTER_SATELLITE_TAGS" /></h5>
      <FormGroupText
        noDesc={true}
        label="NAME"
        onChange={onChange}
        field={"satelliteTags"}
        term={"match"}
        filterId={"satelliteTags"}
      />
      <FormGroupText
        noDesc={true}
        label="SERIAL_NUMBER"
        onChange={onChange}
        field={"satelliteTags"}
        term={"match"}
        filterId={"satelliteTags"}
      />
      <FormGroupText
        noDesc={true}
        label="ARGOS_PPT_NUMBER"
        onChange={onChange}
        field={"satelliteTags"}
        term={"match"}
        filterId={"satelliteTags"}
      />
    </div>
  );
}