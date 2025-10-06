import React, { useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import { Loader } from "@googlemaps/js-api-loader";
import useGetSiteSettings from "../../models/useGetSiteSettings";

export const MapDisplay = observer(
  ({
    store,
    zoom = 4,
    fallbackCenter = { lat: 51, lng: 7 },
    disableUI = true,
  }) => {
    const mapElRef = useRef(null);
    const { data } = useGetSiteSettings();
    const apiKey = data?.googleMapsKey;

    useEffect(() => {
      if (!apiKey) return;
      const loader = new Loader({ apiKey });

      let marker;
      let map;
      let isMounted = true;

      loader.load().then(() => {
        const el = mapElRef.current;
        if (!el || !el.isConnected) return;

        const hasCoords = Number.isFinite(Number(store.lat)) && Number.isFinite(Number(store.lon));
        const center = hasCoords ? { lat: Number(store.lat), lng: Number(store.lon) } : fallbackCenter;

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
        isMounted = false;
        if (marker) marker.setMap(null);
        map = null;
      };
    }, [
      apiKey,
      zoom,
      fallbackCenter.lat,
      fallbackCenter.lng,
      disableUI,
      store.lat,
      store.lon,
    ]);


    return (
      <div
        className="mt-3 mb-3"
        style={{
          width: "100%",
          height: 400,
          borderRadius: 15,
          overflow: "hidden",
        }}
      >
        <div ref={mapElRef} style={{ width: "100%", height: "100%" }} />
      </div>
    );
  },
);
