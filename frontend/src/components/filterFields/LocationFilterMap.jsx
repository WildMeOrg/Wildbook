// import React from "react";
// import { FormattedMessage } from "react-intl";
// import Map from "../Map";
// import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
// import { useEffect, useState } from "react";
// import Description from "../Form/Description";
// import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
// import _ from "lodash-es";
// import { useIntl } from "react-intl";
// import { useSearchQueryParams } from "../../models/useSearchQueryParams";
// import { useStoredFormValue } from "../../models/useStoredFormValue";
// import { observer } from "mobx-react-lite";

// const LocationFilterMap = observer(({ data, store }) => {
//   const [bounds, setBounds] = useState(null);
//   const intl = useIntl();

//   useEffect(() => {
//     if (!bounds) {
//       store.removeFilter("locationMap");
//       return;
//     }

//     // const allFieldsFilled =
//     //   Object.values(bounds).length === 4 &&
//     //   Object.values(bounds).every(
//     //     (value) => value !== undefined && value !== "",
//     //   );
//     // if (Object.values(bounds).length) {
//     store.addFilter("locationMap", "filter", {
//       geo_bounding_box: {
//         locationGeoPoint: {
//           top_left: {
//             lat: bounds.north,
//             lon: bounds.west,
//           },
//           bottom_right: {
//             lat: bounds.south,
//             lon: bounds.east,
//           },
//         },
//       },
//     }, "locationGeoPoint");

//   }, [bounds]);

//   function flattenLocationData(data) {
//     if (!data) {
//       return [];
//     }
//     const result = [];

//     function traverse(locations, depth) {
//       locations.forEach((location) => {
//         const newEntry = {
//           name: location.name,
//           id: location.id,
//           depth: depth,
//         };
//         result.push(newEntry);

//         if (location.locationID && location.locationID.length > 0) {
//           traverse(location.locationID, depth + 1);
//         }
//       });
//     }
//     traverse(data.locationID, 0);
//     return result;
//   }

//   const flattenedData = flattenLocationData(data?.locationData);

//   const locationIDOptions =
//     flattenedData.map((location) => {
//       return {
//         value: location.id,
//         label: _.repeat("-", location.depth) + " " + location.name,
//       };
//     }) || [];
//   const [tempBounds, setTempBounds] = useState(bounds);

//   const getValue = (index) => {
//     const values = store.formFilters.find(
//       (filter) => filter.filterId === "locationMap",
//     )?.query?.geo_bounding_box?.locationGeoPoint;
//     console.log("index", index)
//     if (index === 0) {
//       // console.log(values?.top_left.lat || "");
//       return values?.top_left.lat || ""
//     }
//     if (index === 1) {
//       // console.log(values?.top_left.lon || "");
//       return values?.top_left.lon || ""
//     }
//     if (index === 2) {
//       // console.log(values?.bottom_right.lat || "");
//       return values?.bottom_right.lat || ""
//     }
//     if (index === 3) {
//       // console.log(values?.bottom_right.lon || "");
//       return values?.bottom_right.lon || ""
//     }
//   }

//   console.log("bounds", bounds);

//   return (
//     <div>
//       <h4>
//         <FormattedMessage id="FILTER_LOCATION" />
//       </h4>
//       <Description>
//         <FormattedMessage id="FILTER_LOCATION_DESC" />
//       </Description>
//       <FormLabel>
//         <FormattedMessage id="FILTER_GPS_COORDINATES" />
//       </FormLabel>
//       <div
//         style={{
//           margin: "12px",
//           display: "flex",
//           flexDirection: "row",
//         }}
//       >
//         {[
//           { Northeast_Latitude: "north" },
//           { Northeast_Longitude: "east" },
//           { Southwest_Latitude: "south" },
//           { Southwest_Longitude: "west" },
//         ].map((item, index) => {
//           return (
//             <FormGroup
//               key={index}
//               style={{
//                 marginRight: "10px",
//               }}
//             >
//               <FormLabel>
//                 <FormattedMessage id={Object.keys(item)[0]} />
//               </FormLabel>
//               <FormControl
//                 type="number"
//                 placeholder={
//                   bounds
//                     ? bounds[Object.values(item)[0]]
//                     : intl.formatMessage({ id: "TYPE_NUMBER" })
//                 }
//                 value={getValue(index)}
//                 onChange={(e) => {
//                   console.log("Object.values(item)[0]", Object.values(item)[0]);
//                   console.log("e.target.value", e.target.value);
//                   const newTempBounds = {
//                     ...tempBounds,
//                     [Object.values(item)[0]]: e.target.value,
//                   };
//                   setTempBounds(newTempBounds);
//                   setBounds(newTempBounds);
//                 }}
//               />
//             </FormGroup>
//           );
//         })}
//       </div>
//       <Map
//         bounds={bounds}
//         setBounds={setBounds}
//         setTempBounds={setTempBounds}
//       />
//       <FormGroupMultiSelect
//         isMulti={true}
//         noDesc={true}
//         label="FILTER_LOCATION_ID"
//         options={locationIDOptions}
//         term="terms"
//         field="locationId"
//         filterKey="Location ID"
//         store={store}
//       />
//     </div>
//   );
// });

// export default LocationFilterMap;


import React, { useEffect, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import Map from "../Map";
import { FormGroup, FormLabel, FormControl } from "react-bootstrap";
import Description from "../Form/Description";
import FormGroupMultiSelect from "../Form/FormGroupMultiSelect";
import _ from "lodash-es";
import { observer } from "mobx-react-lite";

const LocationFilterMap = observer(({ data, store }) => {
  const [bounds, setBounds] = useState(null);
  const [tempBounds, setTempBounds] = useState(null);
  const intl = useIntl();

  useEffect(() => {
    if (!bounds) {
      store.removeFilter("locationMap");
      return;
    }

    store.addFilter("locationMap", "filter", {
      geo_bounding_box: {
        locationGeoPoint: {
          top_left: {
            lat: bounds.top_left?.lat,
            lon: bounds.top_left?.lon,
          },
          bottom_right: {
            lat: bounds.bottom_right?.lat,
            lon: bounds.bottom_right?.lon,
          },
        },
      },
    }, "locationGeoPoint");
  }, [bounds]);

  function flattenLocationData(data) {
    if (!data) {
      return [];
    }
    const result = [];

    function traverse(locations, depth) {
      locations.forEach((location) => {
        result.push({
          name: location.name,
          id: location.id,
          depth,
        });

        if (location.locationID && location.locationID.length > 0) {
          traverse(location.locationID, depth + 1);
        }
      });
    }
    traverse(data.locationID, 0);
    return result;
  }

  const flattenedData = flattenLocationData(data?.locationData);
  const locationIDOptions = flattenedData.map((location) => ({
    value: location.id,
    label: _.repeat("-", location.depth) + " " + location.name,
  })) || [];

  const keyMapping = {
    north: "top_left.lat",
    east: "top_left.lon",
    south: "bottom_right.lat",
    west: "bottom_right.lon",
  };

  const getValue = (index) => {
    const values = store.formFilters.find(
      (filter) => filter.filterId === "locationMap",
    )?.query?.geo_bounding_box?.locationGeoPoint;

    return [values?.top_left?.lat, values?.top_left?.lon, values?.bottom_right?.lat, values?.bottom_right?.lon][index] || "";
  };

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
      <div style={{ margin: "12px", display: "flex", flexDirection: "row" }}>
        {[
          { Northeast_Latitude: "north" },
          { Northeast_Longitude: "east" },
          { Southwest_Latitude: "south" },
          { Southwest_Longitude: "west" },
        ].map((item, index) => {
          return (
            <FormGroup key={index} style={{ marginRight: "10px" }}>
              <FormLabel>
                <FormattedMessage id={Object.keys(item)[0]} />
              </FormLabel>
              <FormControl
                type="number"
                placeholder={
                  bounds
                    ? bounds?.[keyMapping[Object.values(item)[0]].split(".")[0]]?.[keyMapping[Object.values(item)[0]].split(".")[1]]
                    : intl.formatMessage({ id: "TYPE_NUMBER" })
                }
                value={getValue(index)}
                onChange={(e) => {
                  const key = Object.values(item)[0];
                  const [mainKey, subKey] = keyMapping[key].split(".");

                  const newTempBounds = {
                    ...tempBounds,
                    [mainKey]: {
                      ...tempBounds?.[mainKey],
                      [subKey]: e.target.value,
                    },
                  };

                  setTempBounds(newTempBounds);
                  setBounds(newTempBounds);
                }}
              />
            </FormGroup>
          );
        })}
      </div>
      <Map bounds={bounds} setBounds={setBounds} setTempBounds={setTempBounds} />
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
