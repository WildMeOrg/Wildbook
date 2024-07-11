
import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";

export default function IdentityFilter() {
  const sightedTimes = <FormattedMessage id="SIGHTED_TIMES" />
  const includeEncounters = <FormattedMessage id="INCLUDE_ENCOUNTERS" />
  return (
    <div>
      <h3><FormattedMessage id="FILTER_IDENTIFY" /></h3>
      <Description>
        <FormattedMessage id="FILTER_IDENTIFY_DESC" />
      </Description>
      <h5><FormattedMessage id="FILTER_MINIMUM_TIMES_SIGHTED" /></h5>
      <Description>
        <FormattedMessage id="FILTER_MINIMUM_TIMES_SIGHTED_DESC" />
      </Description>
      <Form>
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={sightedTimes}
        // checked={isChecked}
        // onChange={handleOnChange}
        />
      </Form>
      <Form>
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          label={includeEncounters}
        // checked={isChecked}
        // onChange={handleOnChange}
        />
      </Form>
      <FormGroupText
        label="FILTER_ALTERNATIVE_ID_CONTAINS"
      />
      <FormGroupText
        label="FILTER_INDIVIDUAL_NAME_CONTAINS"
      />
    </div>
  );
}