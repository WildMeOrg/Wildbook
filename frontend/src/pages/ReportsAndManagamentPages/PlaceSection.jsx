import React, { useEffect, useRef, useState } from "react";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import "./reportEncounter.css";
import { LocationID } from "./LocationID";
import { Alert } from "react-bootstrap";

export const PlaceSection = observer(({ store }) => {
  const { data } = useGetSiteSettings();
  const mapCenterLat = data?.mapCenterLat || 51;
  const mapCenterLon = data?.mapCenterLon || 7;
  const mapZoom = data?.mapZoom || 4;
  const locationData = data?.locationData.locationID;
  const mapKey = data?.googleMapsKey || '';
  const [latAlert, setLatAlert] = useState(false);
  const [lonAlert, setLonAlert] = useState(false);

  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const [map, setMap] = useState(null);
  const [pan, setPan] = useState(false);

  useEffect(() => {
    if (!mapKey) {
      return;
    }
    const loader = new Loader({
      apiKey: mapKey,
      version: "weekly",
    });

    loader
      .load()
      .then(() => {
        const googleMap = new window.google.maps.Map(mapRef.current, {
          center: { lat: mapCenterLat, lng: mapCenterLon },
          zoom: mapZoom,
        });

        googleMap.addListener("click", (e) => {
          setPan(false);
          const lat = e.latLng.lat();
          const lng = e.latLng.lng();
          store.setLat(lat);
          store.setLon(lng);

          if (markerRef.current) {
            markerRef.current.setPosition({ lat, lng });
          } else {
            markerRef.current = new window.google.maps.Marker({
              position: { lat, lng },
              map: googleMap,
            });
          }
        });

        setMap(googleMap);
      })
      .catch((error) => {
        console.error("Error loading Google Maps", error);
      });
  }, [mapCenterLat, mapCenterLon, mapZoom, mapKey]);

  useEffect(() => {
    const lat = parseFloat(store.lat);
    const lng = parseFloat(store.lon);

    if (map && !isNaN(lat) && !isNaN(lng)) {
      if (pan) {
        map.panTo({ lat, lng });
      }

      if (markerRef.current) {
        markerRef.current.setPosition({ lat, lng });
      } else {
        markerRef.current = new window.google.maps.Marker({
          position: { lat, lng },
          map: map,
        });
      }
    } else if (markerRef.current) {
      markerRef.current.setMap(null);
      markerRef.current = null;
    }
  }, [store.lat, store.lon, map, pan]);

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
