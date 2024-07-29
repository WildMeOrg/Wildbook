import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";  
import { useIntl } from "react-intl";


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
  const intl = useIntl();
  return (
    <div>
      <h3><FormattedMessage id="FILTER_TAGS" /></h3>
      <Description>
        <FormattedMessage id="FILTER_TAGS_DESC" />
      </Description>
      <h5><FormattedMessage id="FILTER_METAL_TAGS" /></h5>
      {metalTagLocations.map((location) => {

        return (
          <FormGroup>
            <FormLabel><FormattedMessage id={location.label} defaultMessage="" /></FormLabel>

            <FormControl
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
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

      <div className="w-100 d-flex flex-row gap-2" >
      <FormGroup className="w-50">
            <FormLabel><FormattedMessage id={"FILTER_ACOUSTIC_TAG_SERIAL_NUMBER"} defaultMessage="" /></FormLabel>

            <FormControl
              type="text"
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
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
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
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
      <h5 className="mt-2"><FormattedMessage id="FILTER_SATELLITE_TAGS" /></h5>
      <FormGroupText
        noDesc={true}
        label="FILTER_NAME"
        onChange={onChange}
        field={"satelliteTags.name"}
        term={"match"}
        filterId={"satelliteTags.name"}
      />
      <FormGroupText
        noDesc={true}
        label="FILTER_SERIAL_NUMBER"
        onChange={onChange}
        field={"satelliteTags.serialNumber"}
        term={"match"}
        filterId={"satelliteTags.serialNumber"}
      />
      <FormGroupText
        noDesc={true}
        label="FILTER_ARGOS_PPT_NUMBER"
        onChange={onChange}
        field={"satelliteTags.argosPttNumber"}
        term={"match"}
        filterId={"satelliteTags.argosPttNumber"}
      />
    </div>
  );
}