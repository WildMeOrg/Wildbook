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
      if(!apiKey) return;
      const loader = new Loader({ apiKey });
      let marker;

      loader.load().then(() => {
        const center = store.lat
          ? { lat: store.lat, lng: store.lon }
          : fallbackCenter;

        const map = new window.google.maps.Map(mapElRef.current, {
          center,
          zoom,
          disableDefaultUI: disableUI,
        });

        if (store.lat && store.lon) {
          marker = new window.google.maps.Marker({ position: center, map });
        }
      });

      return () => {
        if (marker) marker.setMap(null);
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
