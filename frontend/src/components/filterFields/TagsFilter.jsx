import React from "react";
import Description from "../Form/Description";
import { FormattedMessage } from "react-intl";
import FormGroupText from "../Form/FormGroupText";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import { useIntl } from "react-intl";
import { observer } from "mobx-react-lite";

const TagsFilter = observer(({ data, store }) => {
  const metalTagLocations =
    data?.metalTagLocation?.map((item) => {
      return {
        value: item,
        label: item,
      };
    }) || [];
  const intl = useIntl();
  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_TAGS" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_TAGS_DESC" />
      </Description>
      <h5>
        <FormattedMessage id="FILTER_METAL_TAGS" />
      </h5>
      {metalTagLocations.map((location) => {
        return (
          <FormGroup key={location?.label}>
            <FormLabel>
              <FormattedMessage id={location.label} defaultMessage="" />
            </FormLabel>

            <FormControl
              type="text"
              value={store.formFilters.find(
                (filter) => filter.filterId === `metalTag.${location.label}`
              )?.query?.bool?.filter?.[1]?.match?.["metalTags.number"] || ""
              }
              placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
              onChange={(e) => {
                if (e.target.value === "") {
                  store.removeFilter(`metalTag.${location.label}`);
                  return;
                }
                store.addFilter(`metalTag.${location.label}`, "nested",
                  {
                    bool: {
                      filter: [
                        {
                          match: {
                            "metalTags.location": location.label,
                          },
                        },
                        {
                          match: {
                            "metalTags.number": e.target.value,
                          },
                        },
                      ],
                    },
                  },
                  "Metal Tags",
                  "metalTags"
                );
              }}
            />
          </FormGroup>
        );
      })}
      <h5>
        <FormattedMessage id="FILTER_ACOUSTIC_TAGS" />
      </h5>

      <div className="w-100 d-flex flex-row gap-2">
        <FormGroup className="w-50">
          <FormLabel>
            <FormattedMessage
              id={"FILTER_ACOUSTIC_TAG_SERIAL_NUMBER"}
              defaultMessage=""
            />
          </FormLabel>

          <FormControl
            type="text"
            placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
            value={store.formFilters.find(
              (filter) => filter.filterId === "acousticTag.serialNumber"
            )?.query?.match?.["acousticTag.serialNumber"] || ""}
            onChange={(e) => {
              if (e.target.value === "") {
                store.removeFilter(`acousticTag.serialNumber`);
                return;
              }

              store.addFilter(`acousticTag.serialNumber`, "filter", {
                match: {
                  "acousticTag.serialNumber": e.target.value,
                },
              }, "Acoustic Tag Serial Number");
            }}
          />
        </FormGroup>
        <FormGroup className="w-50">
          <FormLabel>
            <FormattedMessage id={"FILTER_ACOUSTIC_TAG_ID"} defaultMessage="" />
          </FormLabel>

          <FormControl
            type="text"
            placeholder={intl.formatMessage({ id: "TYPE_HERE" })}
            value={store.formFilters.find(
              (filter) => filter.filterId === "acousticTag.idNumber"
            )?.query?.match?.["acousticTag.idNumber"] || ""}
            onChange={(e) => {
              if (e.target.value === "") {
                store.removeFilter(`acousticTag.idNumber`);
                return;
              }

              store.addFilter(`acousticTag.idNumber`, "filter", {
                match: {
                  "acousticTag.idNumber": e.target.value,
                },
              }, "Acoustic Tag ID");
            }}
          />
        </FormGroup>
      </div>
      <h5 className="mt-2">
        <FormattedMessage id="FILTER_SATELLITE_TAGS" />
      </h5>
      <FormGroupText
        noDesc={true}
        label="FILTER_NAME"
        field={"satelliteTag.name"}
        term={"match"}
        filterId={"satelliteTag.name"}
        filterKey={"Satellite Tag Name"}
        store={store}
      />
      <FormGroupText
        noDesc={true}
        label="FILTER_SERIAL_NUMBER"
        field={"satelliteTag.serialNumber"}
        term={"match"}
        filterId={"satelliteTag.serialNumber"}
        filterKey={"Satellite Tag Serial Number"}
        store={store}
      />
      <FormGroupText
        noDesc={true}
        label="FILTER_ARGOS_PPT_NUMBER"
        field={"satelliteTag.argosPttNumber"}
        term={"match"}
        filterId={"satelliteTag.argosPttNumber"}
        filterKey={"Satellite Tag Argos PTT Number"}
        store={store}
      />
    </div>
  );
});

export default TagsFilter;
