import React, { useEffect, useRef, useState } from "react";
import { Form } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../models/useGetSiteSettings";
import { useIntl } from "react-intl";

export const CoordinatesInput = observer(({ store }) => {
  const intl = useIntl();
  const { data } = useGetSiteSettings();
  const mapCenterLat = data?.mapCenterLat || 51;
  const mapCenterLon = data?.mapCenterLon || 7;
  const mapZoom = data?.mapZoom || 4;
  const mapKey = data?.googleMapsKey || "";

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
    });
    let clickListener = null;

    loader
      .load()
      .then(() => {
        if (!mapRef.current || !mapRef.current.isConnected) return;
        const lat =
          store.lat === null || store.lat === "" ? NaN : Number(store.lat);
        const lng =
          store.lon === null || store.lon === "" ? NaN : Number(store.lon);
        const hasCoords = Number.isFinite(lat) && Number.isFinite(lng);
        const initialCenter = hasCoords
          ? { lat, lng }
          : { lat: mapCenterLat, lng: mapCenterLon };

        const googleMap = new window.google.maps.Map(mapRef.current, {
          center: initialCenter,
          zoom: mapZoom,
        });

        clickListener = googleMap.addListener("click", (e) => {
          setPan(false);
          const lat = e.latLng.lat();
          const lng = e.latLng.lng();
          store.setLat(lat);
          store.setLon(lng);

          if (markerRef.current) {
            markerRef.current.setPosition({ lat, lng });
            markerRef.current.setMap(googleMap);
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
    return () => {
      if (clickListener) {
        window.google.maps.event.removeListener(clickListener);
      }
      if (markerRef.current) {
        markerRef.current.setMap(null);
        markerRef.current = null;
      }
    };
  }, [mapCenterLat, mapCenterLon, mapZoom, mapKey]);

  useEffect(() => {
    const lat =
      store.lat === null || store.lat === "" ? NaN : Number(store.lat);
    const lng =
      store.lon === null || store.lon === "" ? NaN : Number(store.lon);

    if (map && Number.isFinite(lat) && Number.isFinite(lng)) {
      if (pan) {
        map.panTo({ lat, lng });
      }

      if (markerRef.current) {
        markerRef.current.setPosition({ lat, lng });
        markerRef.current.setMap(map);
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

  useEffect(() => {
    const currentGeoPoint =
      store.getFieldValue("location", "locationGeoPoint") || {};

    const newGeoPoint = {
      ...currentGeoPoint,
      lat: store.lat ?? null,
      lon: store.lon ?? null,
    };

    store.setFieldValue("location", "locationGeoPoint", newGeoPoint);
  }, [store.lat, store.lon]);

  return (
    <div>
      <Form.Group>
        <div className="d-flex flex-row gap-3">
          <div className="w-50">
            <h6>
              <FormattedMessage id="LATITUDE" />
            </h6>
            <Form.Control
              type="number"
              required
              placeholder="##.##"
              value={store.lat ?? ""}
              onChange={(e) => {
                const v = e.target.value.trim();
                setPan(true);

                if (v === "") {
                  store.setLat(null);
                } else {
                  const n = Number(v);
                  store.setLat(Number.isFinite(n) ? n : null);
                }
              }}
            />
            {store.errors.getFieldError("location", "latitude") && (
              <div className="invalid-feedback d-block">
                {intl.formatMessage({
                  id: store.errors.getFieldError("location", "latitude"),
                })}
              </div>
            )}
          </div>
          <div className="w-50">
            <h6>
              <FormattedMessage id="LONGITUDE" />
            </h6>
            <Form.Control
              type="number"
              required
              placeholder="##.##"
              value={store.lon ?? ""}
              onChange={(e) => {
                const v = e.target.value.trim();
                setPan(true);

                if (v === "") {
                  store.setLon(null);
                } else {
                  const n = Number(v);
                  store.setLon(Number.isFinite(n) ? n : null);
                }
              }}
            />
            {store.errors.getFieldError("location", "longitude") && (
              <div className="invalid-feedback d-block">
                {intl.formatMessage({
                  id: store.errors.getFieldError("location", "longitude"),
                })}
              </div>
            )}
          </div>
        </div>
      </Form.Group>
      <div
        className="mt-4"
        style={{
          width: "100%",
          height: "200px",
          borderRadius: "15px",
          overflow: "hidden",
        }}
      >
        <div ref={mapRef} style={{ width: "100%", height: "100%" }}></div>
      </div>
    </div>
  );
});

export default CoordinatesInput;
