import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import FormControl from "react-bootstrap/FormControl";
import { useIntl } from "react-intl";

export default function IdentityFilter({ onChange }) {
  const includeEncounters = <FormattedMessage id="FILTER_NO_INDIVIDUAL_ID" />;
  // const [isChecked1, setIsChecked1] = React.useState(false);
  const [isChecked2, setIsChecked2] = React.useState(false);
  // const [times, setTimes] = React.useState(0);
  const intl = useIntl();

  return (
    <div>
      <h3>
        <FormattedMessage id="FILTER_IDENTITY" />
      </h3>
      <Description>
        <FormattedMessage id="FILTER_IDENTITY_DESC" />
      </Description>
      <Form className="d-flex flex-row aligh-items-center">
        {/* <Form.Check className="me-2"
          type="checkbox"
          id="custom-checkbox"
          checked={isChecked1}
          onChange={(e) => {
            setIsChecked1(!isChecked1);
            if (e.target.checked && times) {
              onChange({
                filterId: "individualNumberEncounters",
                clause: "filter",
                filterKey: "Number of Encounters",
                query: {
                  "range": {
                    "individualNumberEncounters": { "gte": times }
                  }
                },
              });
            }else {             
                onChange(null, "individualNumberEncounters");            
            }
          }}
        /> */}
        <FormattedMessage id="FILTER_SIGHTED_AT_LEAST" />
        <FormControl
          type="number"
          style={{
            width: "100px",
            marginLeft: "10px",
            marginRight: "10px",
          }}
          placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
          onChange={(e) => {
            // setTimes(e.target.value);
            if (e.target.value) {
              onChange({
                filterId: "individualNumberEncounters",
                filterKey: "Number of Encounters",
                clause: "filter",
                query: {
                  range: {
                    individualNumberEncounters: { gte: e.target.value },
                  },
                },
              });
            } else {
              onChange(null, "individualNumberEncounters");
            }
          }}
        />
        <FormattedMessage id="FILTER_TIMES" />
      </Form>

      <Form>
        <Form.Check
          label={includeEncounters}
          type="checkbox"
          id="custom-checkbox"
          checked={isChecked2}
          onChange={(e) => {
            setIsChecked2(!isChecked2);
            if (e.target.checked) {
              onChange({
                filterId: "individualId",
                filterKey:
                  "Include only encounters with no assigned Individual ID",
                clause: "must_not",
                query: {
                  exists: {
                    field: "individualId",
                  },
                },
              });
            } else {
              onChange(null, "individualId");
            }
          }}
        />
      </Form>
      <FormGroupText
        label="FILTER_ALTERNATIVE_ID"
        noDesc={true}
        field={"otherCatalogNumbers"}
        term={"match"}
        filterId={"otherCatalogNumbers"}
        onChange={onChange}
        filterKey={"Alternative ID"}
      />
      <FormGroupText
        label="FILTER_INDIVIDUAL_NAME"
        field={"individualNames"}
        term={"match"}
        filterId={"individualNames"}
        onChange={onChange}
        filterKey={"Individual Names"}
      />
    </div>
  );
}
