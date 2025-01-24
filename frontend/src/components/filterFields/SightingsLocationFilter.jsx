import React from "react";
import { FormattedMessage } from "react-intl";
import { useEffect, useRef, useState } from "react";
import { FormControl, FormGroup, FormLabel } from "react-bootstrap";
import { Loader } from "@googlemaps/js-api-loader";
import { useIntl } from "react-intl";

export default function SightingsLocationFilter({ onChange, data }) {

  const intl = useIntl();
  const mapCenterLat = data?.mapCenterLat || 51;
  const mapCenterLon = data?.mapCenterLon || 7;
  const mapZoom = data?.mapZoom || 4;
  const mapKey = data?.googleMapsKey || "";
  const mapRef = useRef(null);
  const markerRef = useRef(null);
  const [map, setMap] = useState(null);
  const [pan, setPan] = useState(false);
  const [location, setLocation] = useState({});

  useEffect(() => {
    const allFieldsFilled =
      Object.values(location).length === 4 &&
      Object.values(location).every(
        (value) => value !== undefined && value !== "",
      );
    if (location && allFieldsFilled) {
      onChange({
        filterId: "sightingslocationMap",
        clause: "term",
        query: {
          geo_distance: {
            lat: location.lat,
            lon: location.lng,
            bearing: location.bearing,
            distance: location.distance,
          },
        },
      });
    } else {
      onChange(null, "sightingslocationMap");
    }
  }, [location]);

  useEffect(() => {
    if (!mapKey) {
      return;
    }
    const loader = new Loader({
      apiKey: mapKey,
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
          setLocation((prevLocation) => ({
            ...prevLocation,
            lat: e.latLng.lat(),
            lng: e.latLng.lng(),
          }));

          if (markerRef.current) {
            markerRef.current.setPosition({ lat: location.lat, lng: location.lng });
          } else {
            markerRef.current = new window.google.maps.Marker({
              position: { lat: location.lat, lng: location.lng },
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

    if (map && !isNaN(location.lat) && !isNaN(location.lng)) {
      if (pan) {
        map.panTo({ lat: location.lat, lng: location.lng });
      }

      if (markerRef.current) {
        markerRef.current.setPosition({ lat: location.lat, lng: location.lng });
      } else {
        markerRef.current = new window.google.maps.Marker({
          position: { lat: location.lat, lng: location.lng },
          map: map,
        });
      }
    } else if (markerRef.current) {
      markerRef.current.setMap(null);
      markerRef.current = null;
    }
  }, [location.lat, location.lng, map, pan]);

  return (
    <div>
      {[
        { LATITUDE: "lat" },
        { LONGITUDE: "lng" },
        { BEARING: "bearing" },
        { DISTANCE: "distance" },
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
                intl.formatMessage({ id: "TYPE_NUMBER" })
              }
              value={location[Object.values(item)[0]]}
              onChange={(e) => {
                console.log("onChange");
                setLocation((prevLocation) => ({
                  ...prevLocation,
                  [Object.values(item)[0]]: e.target.value,
                }));
              }}
            />
          </FormGroup>
        );
      })}

      <div
        className="mt-4"
        style={{
          width: "100%",
          height: "400px",
          borderRadius: "15px",
          overflow: "hidden",
        }}
      >
        <div ref={mapRef} style={{ width: "100%", height: "100%" }}></div>
      </div>
    </div>
  )
}
