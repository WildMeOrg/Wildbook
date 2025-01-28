import React from "react";
import { FormattedMessage } from "react-intl";
import { useEffect, useRef, useState } from "react";
import { FormControl, FormGroup, FormLabel } from "react-bootstrap";
import { Loader } from "@googlemaps/js-api-loader";
import { useIntl } from "react-intl";
import Description from "../Form/Description";

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
      Object.values(location).length === 2 &&
      Object.values(location).every(
        (value) => value !== undefined && value !== "",
      );
    if (location && allFieldsFilled) {
      onChange({
        filterId: "occurrenceLocationGeoPoint",
        clause: "filter",
        filterKey: "sightings location",
        field: "occurrenceLocationGeoPoint",
        query: {
          geo_distance: {
            distance: `10 km`,
            occurrenceLocationGeoPoint: {
              lat: location.lat,
              lon: location.lng,
            },
          },
        },
      });
    } else {
      onChange(null, "occurrenceLocationGeoPoint");
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
            lat,
            lng,
          }));

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
    const lat = parseFloat(location.lat);
    const lng = parseFloat(location.lng);
    if (map && !isNaN(location.lat) && !isNaN(location.lng)) {
      map.panTo({ lat: location.lat, lng: location.lng });
      if (markerRef.current) {
        markerRef.current.setPosition({ lat, lng });
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
      <h4>
        <FormattedMessage id="FILTER_LOCATION" />
      </h4>
      <Description>
        <FormattedMessage id="FILTER_LOCATION_DESC" />
      </Description>
      <div className="d-flex flex-row">
        <FormGroup
          key={"lat"}
          style={{
            marginRight: "10px",
          }}
        >
          <FormLabel>
            <FormattedMessage id="LATITUDE" />
          </FormLabel>
          <FormControl
            type="number"
            placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
            value={location.lat}
            onChange={(e) => {
              setLocation((prevLocation) => ({
                ...prevLocation,
                lat: parseFloat(e.target.value),
              }));
            }}
          />
        </FormGroup>

        <FormGroup
          key={"bearing"}
          style={{
            marginRight: "10px",
          }}
        >
          <FormLabel>
            <FormattedMessage id="LONGITUDE" />
          </FormLabel>
          <FormControl
            type="number"
            placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
            value={location.lng}
            onChange={(e) => {
              setLocation((prevLocation) => ({
                ...prevLocation,
                lng: parseFloat(e.target.value),
              }));
            }}
          />
        </FormGroup>
        <FormGroup
          key={"bearing"}
          style={{
            marginRight: "10px",
          }}
        >
          <FormLabel>
            <FormattedMessage id="BEARING" />
          </FormLabel>
          <FormControl
            type="number"
            placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
            value={location.bearing}
            onChange={(e) => {
              onChange({
                filterId: "occurrenceBearing",
                clause: "filter",
                filterKey: "Sighting Bearing",
                field: "occurrenceBearing",
                query: {
                  term: {
                    occurrenceBearing: e.target.value,
                  },
                },
              });
            }}
          />
        </FormGroup>
        <FormGroup
          key={"distance"}
          style={{
            marginRight: "10px",
          }}
        >
          <FormLabel>
            <FormattedMessage id="DISTANCE" />
          </FormLabel>
          <FormControl
            type="number"
            placeholder={intl.formatMessage({ id: "TYPE_NUMBER" })}
            value={location.distance}
            onChange={(e) => {
              onChange({
                filterId: "occurrenceDistance",
                clause: "filter",
                filterKey: "Sighting Distance",
                field: "occurrenceDistance",
                query: {
                  term: {
                    occurrenceDistance: e.target.value,
                  },
                },
              });
            }}
          />
        </FormGroup>
      </div>
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
  );
}
