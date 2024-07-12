
import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import FormControl from "react-bootstrap/FormControl";

export default function IdentityFilter({
  onChange,

}) {
  const sightedTimes = <FormattedMessage id="SIGHTED_TIMES" />
  const includeEncounters = <FormattedMessage id="INCLUDE_ENCOUNTERS" />
  const [isChecked1, setIsChecked1] = React.useState(false);
  const [isChecked2, setIsChecked2] = React.useState(false);
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
      <Form className="d-flex flex-row aligh-items-center ">
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          checked={isChecked1}
          onChange={() => {
            setIsChecked1(!isChecked1);
          }}
        />
        <FormattedMessage id="SIGHTED_AT_LEAST" />
        <FormControl 
          type="text"
          style={{
            width: "100px",
            marginLeft: "10px",
            marginRight: "10px"
          }}
          placeholder="Type Here"
          onChange={(e) => {
            onChange({
              filterId: "individualNumberEncounters",
              clause: "filter",
              query: {
                "match": {
                  "individualNumberEncounters": e.target.value
                }
              },
            });
          }}
          disabled={!isChecked1}
        />
        <FormattedMessage id="TIMES" />
      </Form>
      
        
      
      <Form>
        <Form.Check
          label={includeEncounters}
          type="checkbox"
          id="custom-checkbox"
          checked={isChecked2}
          onChange={() => {
            setIsChecked2(!isChecked2);
            onChange({
              filterId: "individualId",
              clause: "filter",
              query: {
                "match": {
                  "individualId": !isChecked2
                }
              },
            })
          }}
        />
      </Form>
      <FormGroupText
        label="FILTER_ALTERNATIVE_ID_CONTAINS"
        field={"alternateId"}
        term={"match"}
        filterId={"alternateId"}
        onChange={onChange}

      />
      <FormGroupText
        label="FILTER_INDIVIDUAL_NAME_CONTAINS"
        field={"individualName"}
        term={"match"}
        filterId={"individualName"}
        onChange={onChange}
        noDesc={true}
      />
    </div>
  );
}