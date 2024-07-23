import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";  


export default function TagsFilter({
  data,
  onChange
}) {
  const metalTagLocations = ["left", "right"].map((item) => {
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
          <FormGroup>
            <FormLabel><FormattedMessage id={location.label} defaultMessage="" /></FormLabel>

            <FormControl
              type="text"
              placeholder="Type Here"
              onChange={(e) => {
                if(e.target.value === "") {
                  onChange(null, `metalTag.${location.label}`);
                  return;
                }
                onChange({
                  filterId: `metalTag.${location.label}`,
                  clause: "nested",
                  path: "metalTags",
                  query: {
                      "bool": {
                          "filter": [  
                              {
                                  "match": {
                                      "metalTags.location": location.label
                                  },
                              },  
                              {
                                  "match": {
                                      "metalTags.number": e.target.value
                                  }
                              }
                          ]
                      }
                  }
              })
              }}
            />
          </FormGroup>
        );
      })}
      <h5><FormattedMessage id="FILTER_ACOUSTIC_TAGS" /></h5>
      {/* <FormDualInputs
        label="acousticTags"
        label1="SERIAL_NUMBER"
        label2="ID"
        onChange={onChange}
      /> */}
      <div className="w-100 d-flex flex-row gap-2" >
      <FormGroup className="w-50">
            <FormLabel><FormattedMessage id={"FILTER_ACOUSTIC_TAG_SERIAL_NUMBER"} defaultMessage="" /></FormLabel>

            <FormControl
              type="text"
              placeholder="Type Here"
              onChange={(e) => {
                if(e.target.value === "") {
                  onChange(null, `acousticTag.serialNumber`);
                  return;
                }
                onChange({
                  filterId: "acousticTag.serialNumber",
                  clause: "filter",
                  query: {
                    "match" : {
                      "acousticTag.serialNumber": e.target.value
                    }                    
                  }

                });
              }}
            />
          </FormGroup>
          <FormGroup className="w-50">
            <FormLabel><FormattedMessage id={"FILTER_ACOUSTIC_TAG_ID"} defaultMessage="" /></FormLabel>

            <FormControl
              type="text"
              placeholder="Type Here"
              onChange={(e) => {
                if(e.target.value === "") {
                  onChange(null, `acousticTag.idNumber`);
                  return;
                }
                onChange({
                  filterId: "acousticTag.idNumber",
                  clause: "filter",
                  query: {
                    "match" : {
                      "acousticTag.idNumber": e.target.value
                    }                    
                  }

                });
              }}
            />
          </FormGroup>
      </div>
      <h5><FormattedMessage id="FILTER_SATELLITE_TAGS" /></h5>
      <FormGroupText
        noDesc={true}
        label="NAME"
        onChange={onChange}
        field={"satelliteTags.name"}
        term={"match"}
        filterId={"satelliteTags.name"}
      />
      <FormGroupText
        noDesc={true}
        label="SERIAL_NUMBER"
        onChange={onChange}
        field={"satelliteTags.serialNumber"}
        term={"match"}
        filterId={"satelliteTags.serialNumber"}
      />
      <FormGroupText
        noDesc={true}
        label="ARGOS_PPT_NUMBER"
        onChange={onChange}
        field={"satelliteTags.argosPttNumber"}
        term={"match"}
        filterId={"satelliteTags.argosPttNumber"}
      />
    </div>
  );
}