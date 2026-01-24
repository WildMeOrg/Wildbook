import React, { useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../models/useGetSiteSettings";

export const MapDisplay = observer(({ store, zoom = 4, disableUI = true }) => {
  const mapElRef = useRef(null);
  const { data } = useGetSiteSettings();
  const apiKey = data?.googleMapsKey;
  const defaultCenter = { lat: data?.mapCenterLat, lng: data?.mapCenterLon };

  useEffect(() => {
    if (!apiKey) return;
    const loader = new Loader({ apiKey });

    let marker;
    let map;

    loader.load().then(() => {
      const el = mapElRef.current;
      if (!el || !el.isConnected) return;

      const hasCoords =
        typeof store.lat === "number" &&
        typeof store.lon === "number" &&
        Number.isFinite(store.lat) &&
        Number.isFinite(store.lon);

      const center = hasCoords
        ? { lat: Number(store.lat), lng: Number(store.lon) }
        : defaultCenter;

      map = new window.google.maps.Map(el, {
        center,
        zoom,
        disableDefaultUI: disableUI,
      });

      if (hasCoords) {
        marker = new window.google.maps.Marker({ position: center, map });
      }
    });

    return () => {
      if (marker) marker.setMap(null);
      map = null;
    };
  }, [
    apiKey,
    zoom,
    defaultCenter.lat,
    defaultCenter.lng,
    disableUI,
    store.lat,
    store.lon,
  ]);

  return (
    <div
      className="mt-3 mb-3"
      style={{
        width: "100%",
        height: 200,
        borderRadius: 15,
        overflow: "hidden",
      }}
    >
      <div ref={mapElRef} style={{ width: "100%", height: "100%" }} />
    </div>
  );
});

export default MapDisplay;
