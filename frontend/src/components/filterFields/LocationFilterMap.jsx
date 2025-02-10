import React from "react";
import { FormattedMessage } from "react-intl";
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import { useEffect, useState } from "react";
import Description from "../Form/Description";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import _ from "lodash-es";
import { useIntl } from "react-intl";
import { useSearchQueryParams } from "../../models/useSearchQueryParams";
import { useStoredFormValue } from "../../models/useStoredFormValue";
import { observer } from "mobx-react-lite";

const LocationFilterMap = observer(({  data, store }) => {
  const [bounds, setBounds] = useState(null);
  const intl = useIntl();

  // const paramsObject = useSearchQueryParams();
  // const resultValue = useStoredFormValue(
  //   "locationMap",
  //   "geo_bounding_box",
  //   "locationGeoPoint",
  // );

  // useEffect(() => {
  //   if (paramsObject.searchQueryId && resultValue) {
  //     setTempBounds({
  //       north: resultValue.top_left.lat,
  //       east: resultValue.bottom_right.lon,
  //       south: resultValue.bottom_right.lat,
  //       west: resultValue.top_left.lon,
  //     });
  //   }
  // }, [paramsObject, resultValue]);

  useEffect(() => {
    if (bounds) {
            store.addFilter("locationMap", "filter", {
        geo_bounding_box: {
          locationGeoPoint: {
            top_left: {
              lat: bounds.north,
              lon: bounds.west,
            },
            bottom_right: {
              lat: bounds.south,
              lon: bounds.east,
            },
          },
        },
      }, "locationGeoPoint");
    } else {
      store.removeFilter("locationMap");
    }
  }, [bounds]);

  function flattenLocationData(data) {
    if (!data) {
      return [];
    }
    const result = [];

    function traverse(locations, depth) {
      locations.forEach((location) => {
        const newEntry = {
          name: location.name,
          id: location.id,
          depth: depth,
        };
        result.push(newEntry);

        if (location.locationID && location.locationID.length > 0) {
          traverse(location.locationID, depth + 1);
        }
      });
    }
    traverse(data.locationID, 0);
    return result;
  }

  const flattenedData = flattenLocationData(data?.locationData);

  const locationIDOptions =
    flattenedData.map((location) => {
      return {
        value: location.id,
        label: _.repeat("-", location.depth) + " " + location.name,
      };
    }) || [];
  const [tempBounds, setTempBounds] = useState(bounds);

  return (
    <div>
      <h4>
        <FormattedMessage id="FILTER_LOCATION" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_LOCATION_DESC" />
      </Description>
      <FormLabel>
        <FormattedMessage id="FILTER_GPS_COORDINATES" />
      </FormLabel>
      <div
        style={{
          margin: "12px",
          display: "flex",
          flexDirection: "row",
        }}
      >
        {[
          { Northeast_Latitude: "north" },
          { Northeast_Longitude: "east" },
          { Southwest_Latitude: "south" },
          { Southwest_Longitude: "west" },
        ].map((item, index) => {
          return (
            <FormGroup
              key={index}
              style={{
                marginRight: "10px",
              }}
            >
              <FormLabel>
                <FormattedMessage id={Object.keys(item)[0]} />
              </FormLabel>
              <FormControl
                type="number"
                placeholder={
                  bounds
                    ? bounds[Object.values(item)[0]]
                    : intl.formatMessage({ id: "TYPE_NUMBER" })
                }
                value={
                  bounds
                    ? bounds[Object.values(item)[0]]
                    : tempBounds
                      ? tempBounds[Object.values(item)[0]]
                      : ""
                }
                onChange={(e) => {
                  const newTempBounds = {
                    ...tempBounds,
                    [Object.values(item)[0]]: e.target.value,
                  };
                  setTempBounds(newTempBounds);

                  // Check if all fields have values
                  const allFieldsFilled =
                    Object.values(newTempBounds).length === 4 &&
                    Object.values(newTempBounds).every(
                      (value) => value !== undefined && value !== "",
                    );
                  if (allFieldsFilled) {
                    setBounds(newTempBounds);
                  }
                }}
              />
            </FormGroup>
          );
        })}
      </div>
      <Map
        bounds={bounds}
        setBounds={setBounds}
        setTempBounds={setTempBounds}
      />
      <FormGroupMultiSelect
        isMulti={true}
        noDesc={true}
        label="FILTER_LOCATION_ID"
        options={locationIDOptions}
        term="terms"
        field="locationId"
        filterKey="Location ID"
        store={store}
      />
    </div>
  );
});

export default LocationFilterMap;
