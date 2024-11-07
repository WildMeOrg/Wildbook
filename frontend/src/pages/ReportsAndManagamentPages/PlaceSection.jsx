import React, { useEffect, useState } from "react";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import "./reportEncounter.css";
import { LocationID } from "./LocationID";

const MyPin = React.memo(() => {
  return (
    <i
      className="bi bi-geo-alt-fill"
      style={{
        fontSize: "20px",
        color: "red",
        position: "absolute",
        top: "-30px",
        left: "-10px",
      }}
    ></i>
  );
});

MyPin.displayName = "MyPin";

export const PlaceSection = observer(({ store }) => {
  const { data } = useGetSiteSettings();
  const mapCenterLat = data?.mapCenterLat;
  const mapCenterLon = data?.mapCenterLon;
  const mapZoom = data?.mapZoom;
  const key = data?.googleMapsKey;
  const locationData = data?.locationData.locationID;

  console.log(key, "key");

  return (
    <div>
      <LocationID
        store={store}
        locationData={locationData}
        mapCenterLat={mapCenterLat}
        mapCenterLon={mapCenterLon}
        mapZoom={mapZoom}
      />      
    </div>
  );
});

export default PlaceSection;
