
import React from "react";
import { FormattedMessage } from "react-intl";
import Form from "react-bootstrap/Form";
import Description from "../Form/Description";
import FormGroupText from "../Form/FormGroupText";
import FormControl from "react-bootstrap/FormControl";
import { useContext } from "react";
import FilterContext from "../../FilterContextProvider";

export default function IdentityFilter({
  onChange,

}) {
  const includeEncounters = <FormattedMessage id="INCLUDE_ENCOUNTERS" />
  const [isChecked1, setIsChecked1] = React.useState(false);
  const [isChecked2, setIsChecked2] = React.useState(false);
  const { filters, updateFilter } = useContext(FilterContext);

  return (
    <div>
      <h3><FormattedMessage id="FILTER_IDENTIFY" /></h3>
      <Description>
        <FormattedMessage id="FILTER_IDENTIFY_DESC" />
      </Description>
      <Form className="d-flex flex-row aligh-items-center ">
        <Form.Check
          type="checkbox"
          id="custom-checkbox"
          checked={isChecked1}
          onChange={(e) => {
            setIsChecked1(!isChecked1);

            // onChange({
            //   filterId: "individualNumberEncounters",
            //   clause: "filter",
            //   query: {
            //     "match": {
            //       "individualID": null
            //     }
            //   },
            // })
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
          // checked={filters.individualNumberEncounters}
          placeholder="Type Here"
          onChange={(e) => {
            if (isChecked1 && e.target.value) {
              console.log("checked and ", e.target.value);
              onChange({
                filterId: "individualNumberEncounters",
                clause: "filter",
                query: {
                  "range": {
                    "individualNumberEncounters": { "gte": e.target.value }
                  }
                },
              });
            }
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
          onChange={(e) => {
            setIsChecked2(!isChecked2);
            if (e.target.checked) {
              console.log("checked and ", e.target.value);
              onChange({
                filterId: "individualId",
                clause: "filter",
                query: {
                  "exists": {
                    "field": "individualId"
                  }
                },
              })

            }
          }}
        />

        {/* <FormControl
  type="checkbox"
  id="custom-checkbox"
  checked={isChecked2}
  label={includeEncounters}
  onChange={(e) => {
    const newChecked = e.target.checked;
    if(newChecked){
      onChange({
        filterId: "individualId",
        clause: "filter",
        query: {
          "match": {
            "individualId": null  
          }
        }
      });
    } else {      
      onChange({
        filterId: "individualId",
        clause: "filter",
        query: {}
      });
    }
  }}
/> */}

      </Form>
      <FormGroupText
        label="FILTER_ALTERNATIVE_ID_CONTAINS"
        field={"otherCatalogNumbers"}
        term={"match"}
        filterId={"otherCatalogNumbers"}
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