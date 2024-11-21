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
  const mapKey = data?.googleMapsKey || "";
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
      <Form.Group>
        <Form.Label>
          <FormattedMessage id="FILTER_GPS_COORDINATES" />
        </Form.Label>
        <div className="d-flex flex-row gap-3">
          <div className="w-50">
            <Form.Control
              type="number"
              required
              placeholder="##.##"
              value={
                store.lat !== null && store.lat !== undefined ? store.lat : ""
              }
              onChange={(e) => {
                setLatAlert(false);
                let newLat = e.target.value;
                setPan(true);
                store.setLat(newLat);
                if (newLat < -90 || newLat > 90) {
                  setLatAlert(true);
                }
              }}
            />
            {latAlert && (
              <Alert
                variant="light"
                className="d-inline-block p-2 mt-2"
                style={{
                  color: "#dc3545",
                  width: "auto",
                  border: "none",
                }}
              >
                <strong>
                  <FormattedMessage id="INVALID_LAT" />
                </strong>
              </Alert>
            )}
          </div>
          <div className="w-50">
            <Form.Control
              type="number"
              required
              placeholder="##.##"
              value={
                store.lon !== null && store.lon !== undefined ? store.lon : ""
              }
              onChange={(e) => {
                setLonAlert(false);
                const newLon = e.target.value;
                setPan(true);
                store.setLon(newLon);
                if (newLon < -180 || newLon > 180) {
                  setLonAlert(true);
                }
              }}
            />
            {lonAlert && (
              <Alert
                variant="light"
                className="d-inline-block p-2 mt-2"
                style={{
                  color: "#dc3545",
                  width: "auto",
                  border: "none",
                }}
              >
                <strong>
                  <FormattedMessage id="INVALID_LONG" />
                </strong>
              </Alert>
            )}
          </div>
        </div>
      </Form.Group>

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
});

export default PlaceSection;
